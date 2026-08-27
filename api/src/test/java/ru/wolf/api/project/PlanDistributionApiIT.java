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
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.gantt.WeekPlan;
import ru.wolf.api.gantt.WeekPlanRepository;
import ru.wolf.api.lifearea.LifeArea;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.project.dto.*;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

class PlanDistributionApiIT extends ApiIntegrationTest {

    @Autowired ProjectRepository projectRepository;
    @Autowired LifeAreaRepository lifeAreaRepository;
    @Autowired WeekPlanRepository weekPlanRepository;
    @Autowired UserRepository userRepository;

    private User admin;
    private LifeArea area;

    @BeforeEach
    void cleanup() {
        weekPlanRepository.deleteAll();
        projectRepository.deleteAll();
        lifeAreaRepository.deleteAll();
        admin = userRepository.findByUsername("admin").orElseThrow();
        admin.setAvailableWeeklyHours(new BigDecimal("15"));
        userRepository.save(admin);
        area = lifeAreaRepository.save(LifeArea.builder().user(admin).name("Работа").sortOrder(0).build());
    }

    @Test
    void even_weekdays_distributes_forty_hours_across_four_weeks() {
        WebTestClient client = authedAdminClient();
        Map body = Map.of("lifeAreaId", area.getId(), "title", "Проект", "startDate", "2026-03-02",
                "endDate", "2026-03-27", "totalPlanHours", 40);
        ProjectResponse project = client.post().uri("/api/v1/projects").bodyValue(body)
                .exchange().expectStatus().isOk().expectBody(ProjectResponse.class).returnResult().getResponseBody();

        Map result = client.post().uri("/api/v1/projects/{id}/plan-distribution", project.id())
                .bodyValue(Map.of("mode", "even_weekdays")).exchange().expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        List<Map> weeks = (List<Map>) result.get("weeks");

        assertThat(weeks).hasSize(4);
        assertThat(weeks).extracting(item -> ((Number) item.get("hours")).doubleValue())
                .containsExactly(10.0, 10.0, 10.0, 10.0);
        assertThat(weekPlanRepository.findAll()).hasSize(4);
    }

    @Test
    void even_all_days_rounds_to_quarter_hours_and_preserves_total() {
        WebTestClient client = authedAdminClient();
        Project project = projectRepository.save(Project.builder().user(admin).lifeArea(area).title("Десять дней")
                .startDate(LocalDate.of(2026, 3, 2)).endDate(LocalDate.of(2026, 3, 11))
                .totalPlanHours(new BigDecimal("15")).build());

        client.post().uri("/api/v1/projects/{id}/plan-distribution", project.getId())
                .bodyValue(Map.of("mode", "even_all_days")).exchange().expectStatus().isOk();

        BigDecimal total = weekPlanRepository.findAll().stream().map(WeekPlan::getPlanHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("15.00");
        assertThat(weekPlanRepository.findAll()).allSatisfy(plan -> assertThat(plan.getPlanHours().remainder(BigDecimal.valueOf(.25))).isZero());
    }

    @Test
    void capacity_sums_active_projects_and_routines_against_available_hours() {
        Project first = projectRepository.save(Project.builder().user(admin).lifeArea(area).title("Первый")
                .status(Project.Status.IN_PROGRESS).build());
        Project second = projectRepository.save(Project.builder().user(admin).lifeArea(area).title("Второй")
                .status(Project.Status.IN_PROGRESS).build());
        weekPlanRepository.save(WeekPlan.builder().user(admin).project(first).isoYear(2026).isoWeek(11)
                .planHours(new BigDecimal("10")).build());
        weekPlanRepository.save(WeekPlan.builder().user(admin).project(second).isoYear(2026).isoWeek(11)
                .planHours(new BigDecimal("10")).build());

        Map row = authedAdminClient().get().uri("/api/v1/planning/capacity?from=2026-03-09&to=2026-03-15")
                .exchange().expectStatus().isOk().expectBodyList(Map.class).returnResult().getResponseBody().get(0);

        assertThat(((Number) row.get("plannedHours")).doubleValue()).isEqualTo(20.0);
        assertThat(((Number) row.get("availableHours")).doubleValue()).isEqualTo(15.0);
        assertThat(((Number) row.get("delta")).doubleValue()).isEqualTo(-5.0);
    }

    @Test
    void project_without_dates_is_rejected() {
        WebTestClient client = authedAdminClient();
        Project project = projectRepository.save(Project.builder().user(admin).lifeArea(area).title("Без дат").totalPlanHours(new BigDecimal("5")).build());
        client.post().uri("/api/v1/projects/{id}/plan-distribution", project.getId())
                .bodyValue(Map.of("mode", "even_all_days")).exchange().expectStatus().isBadRequest();
    }
}

