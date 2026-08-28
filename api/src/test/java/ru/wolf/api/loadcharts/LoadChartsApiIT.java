/*
 * WOLF — Wolf's Own Life Framework
 * Copyright (C) 2025 Pavel Obukhov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.loadcharts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloProject;
import ru.wolf.api.delo.DeloProjectId;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.lifearea.LifeArea;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.loadcurve.LoadCurveEntry;
import ru.wolf.api.loadcurve.LoadCurveEntryRepository;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.routine.Routine;
import ru.wolf.api.routine.RoutineRepository;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class LoadChartsApiIT extends ApiIntegrationTest {

    @Autowired UserRepository userRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired LifeAreaRepository lifeAreaRepository;
    @Autowired RoutineRepository routineRepository;
    @Autowired DeloRepository deloRepository;
    @Autowired DeloProjectRepository deloProjectRepository;
    @Autowired LoadCurveEntryRepository loadCurveEntryRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private final ObjectMapper mapper = new ObjectMapper();

    private User makeUser(String username) {
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode("secret"));
        u.setTimezone("Europe/Moscow");
        u.setNightStart(LocalTime.of(23, 0));
        u.setNightEnd(LocalTime.of(7, 0));
        u.setHourAccountingMode("PRIMARY_ONLY");
        u.setAvailableWeeklyHours(new BigDecimal("30"));
        u.setHoursPerDelo(new BigDecimal("1.5"));
        return userRepository.save(u);
    }

    private LifeArea makeArea(User u, String name) {
        return lifeAreaRepository.save(LifeArea.builder().user(u).name(name).build());
    }

    private Project makeProject(User u, LifeArea a, String title, BigDecimal totalPlanHours) {
        return projectRepository.save(Project.builder().user(u).lifeArea(a).title(title)
                .status(Project.Status.IN_PROGRESS).totalPlanHours(totalPlanHours).build());
    }

    private void attachDelo(User u, Project p, boolean primary) {
        // Связка Delo↔Project — @MapsId: id проставляется явно (паттерн DataSyncImportApplyService),
        // иначе deloId остаётся null и flush падает с NPE.
        Delo delo = deloRepository.save(Delo.builder().user(u).title("Дело " + p.getId() + "-" + primary).build());
        deloProjectRepository.save(DeloProject.builder()
                .id(new DeloProjectId(delo.getId(), p.getId()))
                .delo(delo).project(p).isPrimary(primary).build());
    }

    /** Полный ответ как JsonNode. */
    private JsonNode loadCharts(WebTestClient client) {
        String body = client.get().uri("/api/v1/planning/load-charts")
                .exchange().expectStatus().isOk()
                .returnResult(String.class).getResponseBody().blockFirst();
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Индекс проекта по title в массиве projects[]. */
    private int projectIndex(JsonNode root, String title) {
        JsonNode projects = root.get("projects");
        for (int i = 0; i < projects.size(); i++) {
            if (projects.get(i).get("title").asText().equals(title)) return i;
        }
        throw new IllegalStateException("project not found: " + title);
    }

    private JsonNode project(JsonNode root, String title) {
        return root.get("projects").get(projectIndex(root, title));
    }

    @Test
    void effort_source_plan_vs_estimated_and_empty() {
        User u = makeUser("lc-effort");
        LifeArea a = makeArea(u, "Заработок");
        makeProject(u, a, "С планом", new BigDecimal("145"));
        Project estimated = makeProject(u, a, "Оценка", null);
        attachDelo(u, estimated, true);
        attachDelo(u, estimated, true);
        makeProject(u, a, "Пустой", null);

        JsonNode root = loadCharts(authedClient("lc-effort", "secret"));
        assertThat(root.get("hoursPerDelo").decimalValue()).isEqualByComparingTo("1.5");
        assertThat(root.get("projects").size()).isEqualTo(3);

        JsonNode planned = project(root, "С планом");
        assertThat(planned.get("effortSource").asText()).isEqualTo("PLAN");
        assertThat(planned.get("effortHours").decimalValue()).isEqualByComparingTo("145.00");

        JsonNode est = project(root, "Оценка");
        assertThat(est.get("effortSource").asText()).isEqualTo("ESTIMATED");
        assertThat(est.get("deloCount").asLong()).isEqualTo(2L);
        assertThat(est.get("effortHours").decimalValue()).isEqualByComparingTo("3.00"); // 2 * 1.5

        JsonNode empty = project(root, "Пустой");
        assertThat(empty.get("effortHours").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(empty.get("forecastByRate").isNull()).isTrue();
        assertThat(empty.get("forecastByCurve").isNull()).isTrue();
    }

    @Test
    void remaining_never_negative() {
        User u = makeUser("lc-remain");
        LifeArea a = makeArea(u, "Заработок");
        makeProject(u, a, "Проект", new BigDecimal("10"));

        JsonNode root = loadCharts(authedClient("lc-remain", "secret"));
        JsonNode p = project(root, "Проект");
        assertThat(p.get("remainingHours").decimalValue()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    void hour_accounting_mode_changes_delo_count() {
        User u = makeUser("lc-mode");
        LifeArea a = makeArea(u, "Заработок");
        Project p1 = makeProject(u, a, "Основной", null);
        Project p2 = makeProject(u, a, "Вторичный", null);
        attachDelo(u, p1, true);
        attachDelo(u, p2, false);

        WebTestClient c = authedClient("lc-mode", "secret");
        JsonNode primary = loadCharts(c);
        assertThat(project(primary, "Основной").get("deloCount").asLong()).isEqualTo(1L);
        assertThat(project(primary, "Вторичный").get("deloCount").asLong()).isEqualTo(0L);

        Map<String, Object> req = new HashMap<>();
        req.put("timezone", "Europe/Moscow");
        req.put("nightStart", "23:00:00");
        req.put("nightEnd", "07:00:00");
        req.put("hourAccountingMode", "ALL_PROJECTS");
        c.put().uri("/api/v1/settings").bodyValue(req).exchange().expectStatus().isOk();

        JsonNode all = loadCharts(c);
        assertThat(project(all, "Основной").get("deloCount").asLong()).isEqualTo(1L);
        assertThat(project(all, "Вторичный").get("deloCount").asLong()).isEqualTo(1L);
    }

    @Test
    void monthly_load_includes_routine_and_over_limit() {
        User u = makeUser("lc-month");
        u.setAvailableWeeklyHours(new BigDecimal("10"));
        u = userRepository.save(u);
        LifeArea a = makeArea(u, "Здоровье");
        routineRepository.save(Routine.builder().user(u).title("Спорт").weeklyHours(new BigDecimal("6")).archived(false).build());
        makeProject(u, a, "Проект", new BigDecimal("100"));

        JsonNode root = loadCharts(authedClient("lc-month", "secret"));
        assertThat(root.get("routines").size()).isEqualTo(1);
        assertThat(root.get("routines").get(0).get("weeklyHours").decimalValue()).isEqualByComparingTo("6.00");
        // рутина 6*4.33 ≈ 25.98 > 10 → overLimit
        assertThat(root.get("monthlyLoad").get(0).get("overLimit").asBoolean()).isTrue();
    }

    @Test
    void forecast_by_curve_from_stepped_curve() {
        User u = makeUser("lc-curve");
        LifeArea a = makeArea(u, "Заработок");
        Project p = makeProject(u, a, "Кривая", new BigDecimal("200"));
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        loadCurveEntryRepository.saveAll(List.of(
                LoadCurveEntry.builder().project(p).weekStart(monday).hours(new BigDecimal("16")).build(),
                LoadCurveEntry.builder().project(p).weekStart(monday.plusWeeks(4)).hours(new BigDecimal("8")).build()));

        WebTestClient c = authedClient("lc-curve", "secret");
        JsonNode root = loadCharts(c);
        assertThat(project(root, "Кривая").get("forecastByCurve").isNull()).isFalse();

        // кривая, уходящая в 0 → не закрывается за короткий горизонт
        loadCurveEntryRepository.save(LoadCurveEntry.builder()
                .project(p).weekStart(monday.plusWeeks(8)).hours(BigDecimal.ZERO).build());
        JsonNode shortRoot = loadCharts(c.mutate().build());
        // повторный запрос с horizon=2 вернёт null (не накопили remaining)
        String body = c.get().uri("/api/v1/planning/load-charts?horizonMonths=2")
                .exchange().expectStatus().isOk()
                .returnResult(String.class).getResponseBody().blockFirst();
        try {
            JsonNode r2 = mapper.readTree(body);
            assertThat(project(r2, "Кривая").get("forecastByCurve").isNull()).isTrue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void tenant_isolation_no_other_users_data() {
        User owner = makeUser("lc-owner");
        LifeArea a = makeArea(owner, "Заработок");
        makeProject(owner, a, "Мой проект", new BigDecimal("50"));
        User other = makeUser("lc-other");
        LifeArea a2 = makeArea(other, "Заработок");
        makeProject(other, a2, "Чужой проект", new BigDecimal("999"));

        JsonNode root = loadCharts(authedClient("lc-owner", "secret"));
        assertThat(root.get("projects").size()).isEqualTo(1);
        assertThat(root.get("projects").get(0).get("title").asText()).isEqualTo("Мой проект");
    }
}
