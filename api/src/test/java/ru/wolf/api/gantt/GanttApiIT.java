package ru.wolf.api.gantt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloController;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.lifearea.LifeAreaController;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.project.ProjectController;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryController;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class GanttApiIT extends ApiIntegrationTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    @Autowired
    WeekPlanRepository weekPlanRepository;

    @Autowired
    TimeEntryRepository timeEntryRepository;

    @Autowired
    DeloRepository deloRepository;

    @Autowired
    DeloProjectRepository deloProjectRepository;

    @Autowired
    ProjectRepository projectRepository;

    @Autowired
    LifeAreaRepository lifeAreaRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanup() {
        timeEntryRepository.deleteAll();
        weekPlanRepository.deleteAll();
        deloProjectRepository.deleteAll();
        deloRepository.deleteAll();
        projectRepository.deleteAll();
        lifeAreaRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> !"admin".equals(u.getUsername()))
                .forEach(userRepository::delete);
        userRepository.findByUsername("admin").ifPresent(u -> {
            u.setTimezone("Europe/Moscow");
            u.setHourAccountingMode("PRIMARY_ONLY");
            u.setDayEnd(LocalTime.MIDNIGHT);
            u.setNightStart(LocalTime.of(23, 0));
            u.setNightEnd(LocalTime.of(7, 0));
            u.setDefaultSleepEnd(LocalTime.of(9, 0));
            userRepository.save(u);
        });
    }

    @Test
    void gantt_default_starts_previous_monday() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        createProject(authed, areaId, null, "WOLF");

        GanttController.GanttResponse body = getGantt(authed, null);

        assertThat(body).isNotNull();
        assertThat(body.getHourAccountingMode()).isEqualTo("PRIMARY_ONLY");
        assertThat(body.getTimezone()).isEqualTo("Europe/Moscow");
        assertThat(body.getWeeks()).isNotEmpty();

        LocalDate today = LocalDate.now(MOSCOW);
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate prevMonday = thisMonday.minusWeeks(1);
        assertThat(body.getRangeStart()).isEqualTo(prevMonday.toString());
        assertThat(body.getWeeks().get(0).getWeekStart()).isEqualTo(prevMonday.toString());

        // Exactly one current week flagged
        long currentCount = body.getWeeks().stream().filter(GanttController.WeekColumn::isCurrent).count();
        assertThat(currentCount).isEqualTo(1);

        assertThat(body.getProjects()).hasSize(1);
        assertThat(body.getProjects().get(0).getTitle()).isEqualTo("WOLF");
        assertThat(body.getProjects().get(0).getCells()).hasSize(body.getWeeks().size());
    }

    @Test
    void week_plan_upsert_and_read_on_gantt() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        Long projectId = createProject(authed, areaId, null, "План-тест").getId();

        LocalDate monday = LocalDate.of(2026, 3, 9); // ISO week 2026-W11
        int isoYear = monday.get(WeekFields.ISO.weekBasedYear());
        int isoWeek = monday.get(WeekFields.ISO.weekOfWeekBasedYear());

        GanttController.WeekPlanResponse written = authed.put()
                .uri("/api/v1/gantt/week-plans")
                .bodyValue(Map.of(
                        "projectId", projectId,
                        "isoYear", isoYear,
                        "isoWeek", isoWeek,
                        "planHours", new BigDecimal("8.50")
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody(GanttController.WeekPlanResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(written).isNotNull();
        assertThat(written.getProjectId()).isEqualTo(projectId);
        assertThat(written.getPlanHours()).isEqualByComparingTo("8.50");

        GanttController.GanttResponse gantt = getGantt(authed, "from=" + monday + "&weeks=4");
        GanttController.ProjectRow row = findProject(gantt, projectId);
        GanttController.CellHours cell = findCell(row, isoYear, isoWeek);
        assertThat(cell.getPlanHours()).isEqualByComparingTo("8.50");
        assertThat(cell.getFactHours()).isEqualByComparingTo("0.00");

        // Update
        authed.put()
                .uri("/api/v1/gantt/week-plans")
                .bodyValue(Map.of(
                        "projectId", projectId,
                        "isoYear", isoYear,
                        "isoWeek", isoWeek,
                        "planHours", 12
                ))
                .exchange()
                .expectStatus().isOk();

        gantt = getGantt(authed, "from=" + monday + "&weeks=4");
        cell = findCell(findProject(gantt, projectId), isoYear, isoWeek);
        assertThat(cell.getPlanHours()).isEqualByComparingTo("12.00");

        // Clear with 0
        GanttController.WeekPlanResponse cleared = authed.put()
                .uri("/api/v1/gantt/week-plans")
                .bodyValue(Map.of(
                        "projectId", projectId,
                        "isoYear", isoYear,
                        "isoWeek", isoWeek,
                        "planHours", 0
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody(GanttController.WeekPlanResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(cleared.getPlanHours()).isNull();

        gantt = getGantt(authed, "from=" + monday + "&weeks=4");
        cell = findCell(findProject(gantt, projectId), isoYear, isoWeek);
        assertThat(cell.getPlanHours()).isNull();
        assertThat(weekPlanRepository.count()).isZero();
    }

    @Test
    void fact_primary_only_counts_primary_project() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        Long p1 = createProject(authed, areaId, null, "Primary").getId();
        Long p2 = createProject(authed, areaId, null, "Secondary").getId();

        Long deloId = createDelo(authed, "Код", List.of(p1, p2), p1);

        // Monday 2026-03-09, 10:00–12:00 = 2h DONE
        LocalDate monday = LocalDate.of(2026, 3, 9);
        int isoYear = monday.get(WeekFields.ISO.weekBasedYear());
        int isoWeek = monday.get(WeekFields.ISO.weekOfWeekBasedYear());
        putEntry(authed, monday.atTime(10, 0), monday.atTime(12, 0), deloId, TimeEntry.Status.DONE);

        GanttController.GanttResponse gantt = getGantt(authed, "from=" + monday + "&weeks=2");

        GanttController.CellHours c1 = findCell(findProject(gantt, p1), isoYear, isoWeek);
        GanttController.CellHours c2 = findCell(findProject(gantt, p2), isoYear, isoWeek);
        assertThat(c1.getFactHours()).isEqualByComparingTo("2.00");
        assertThat(c2.getFactHours()).isEqualByComparingTo("0.00");
    }

    @Test
    void fact_all_projects_counts_full_hours_on_each_linked_project() {
        WebTestClient authed = authedAdminClient();
        // Switch mode
        userRepository.findByUsername("admin").ifPresent(u -> {
            u.setHourAccountingMode("ALL_PROJECTS");
            userRepository.save(u);
        });

        Long areaId = createLifeArea(authed, "Работа");
        Long p1 = createProject(authed, areaId, null, "A").getId();
        Long p2 = createProject(authed, areaId, null, "B").getId();
        Long deloId = createDelo(authed, "Общее", List.of(p1, p2), p1);

        LocalDate monday = LocalDate.of(2026, 3, 9);
        int isoYear = monday.get(WeekFields.ISO.weekBasedYear());
        int isoWeek = monday.get(WeekFields.ISO.weekOfWeekBasedYear());
        // 1.5h
        putEntry(authed, monday.atTime(14, 0), monday.atTime(15, 30), deloId, TimeEntry.Status.DONE);

        GanttController.GanttResponse gantt = getGantt(authed, "from=" + monday + "&weeks=2");
        assertThat(gantt.getHourAccountingMode()).isEqualTo("ALL_PROJECTS");

        assertThat(findCell(findProject(gantt, p1), isoYear, isoWeek).getFactHours())
                .isEqualByComparingTo("1.50");
        assertThat(findCell(findProject(gantt, p2), isoYear, isoWeek).getFactHours())
                .isEqualByComparingTo("1.50");
    }

    @Test
    void ad_hoc_excluded_from_project_fact() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        Long p1 = createProject(authed, areaId, null, "Проект").getId();
        Long deloId = createDelo(authed, "Связанное", List.of(p1), p1);

        LocalDate monday = LocalDate.of(2026, 3, 9);
        int isoYear = monday.get(WeekFields.ISO.weekBasedYear());
        int isoWeek = monday.get(WeekFields.ISO.weekOfWeekBasedYear());

        // 2h ad-hoc + 1h delo
        putAdHoc(authed, monday.atTime(9, 0), monday.atTime(11, 0), "Без дела", TimeEntry.Status.DONE);
        putEntry(authed, monday.atTime(11, 0), monday.atTime(12, 0), deloId, TimeEntry.Status.DONE);

        GanttController.GanttResponse gantt = getGantt(authed, "from=" + monday + "&weeks=2");
        GanttController.CellHours cell = findCell(findProject(gantt, p1), isoYear, isoWeek);
        assertThat(cell.getFactHours()).isEqualByComparingTo("1.00");
    }

    @Test
    void planned_entries_not_counted_as_fact() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        Long p1 = createProject(authed, areaId, null, "План").getId();
        Long deloId = createDelo(authed, "Будущее", List.of(p1), p1);

        LocalDate monday = LocalDate.of(2026, 3, 9);
        int isoYear = monday.get(WeekFields.ISO.weekBasedYear());
        int isoWeek = monday.get(WeekFields.ISO.weekOfWeekBasedYear());

        putEntry(authed, monday.atTime(10, 0), monday.atTime(12, 0), deloId, TimeEntry.Status.PLANNED);

        GanttController.GanttResponse gantt = getGantt(authed, "from=" + monday + "&weeks=2");
        assertThat(findCell(findProject(gantt, p1), isoYear, isoWeek).getFactHours())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void filter_by_life_area_and_only_with_dates() {
        WebTestClient authed = authedAdminClient();
        Long work = createLifeArea(authed, "Работа");
        Long health = createLifeArea(authed, "Здоровье");

        Long pWork = createProject(authed, work, null, "Код").getId();
        Long pHealthDated = createProjectWithDates(authed, health, null, "Бег",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)).getId();
        createProject(authed, health, null, "Без сроков"); // undated health

        // Filter life area = health
        GanttController.GanttResponse byArea = getGantt(authed, "lifeAreaIds=" + health + "&weeks=4");
        assertThat(byArea.getProjects()).extracting(GanttController.ProjectRow::getId)
                .contains(pHealthDated)
                .doesNotContain(pWork);

        // onlyWithDates
        GanttController.GanttResponse dated = getGantt(authed, "onlyWithDates=true&weeks=4");
        assertThat(dated.getProjects()).extracting(GanttController.ProjectRow::getId)
                .containsExactly(pHealthDated);
    }

    @Test
    void nested_projects_have_depth() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        Long root = createProject(authed, areaId, null, "Корень").getId();
        Long child = createProject(authed, areaId, root, "Дочка").getId();

        GanttController.GanttResponse gantt = getGantt(authed, "weeks=4");
        Map<Long, GanttController.ProjectRow> byId = new HashMap<>();
        for (GanttController.ProjectRow r : gantt.getProjects()) {
            byId.put(r.getId(), r);
        }
        assertThat(byId.get(root).getDepth()).isEqualTo(0);
        assertThat(byId.get(child).getDepth()).isEqualTo(1);
        assertThat(byId.get(child).getParentId()).isEqualTo(root);
    }

    @Test
    void week_plan_foreign_project_rejected() {
        WebTestClient admin = authedAdminClient();
        Long areaId = createLifeArea(admin, "Работа");
        Long adminProject = createProject(admin, areaId, null, "Чужой").getId();

        User user2 = new User();
        user2.setUsername("user2");
        user2.setPasswordHash(passwordEncoder.encode("admin"));
        user2.setTimezone("Europe/Moscow");
        user2.setHourAccountingMode("PRIMARY_ONLY");
        user2.setNightStart(LocalTime.of(23, 0));
        user2.setNightEnd(LocalTime.of(7, 0));
        userRepository.save(user2);

        WebTestClient client2 = authedClient("user2", "admin");
        client2.put()
                .uri("/api/v1/gantt/week-plans")
                .bodyValue(Map.of(
                        "projectId", adminProject,
                        "isoYear", 2026,
                        "isoWeek", 11,
                        "planHours", 5
                ))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void unauthenticated_access_rejected() {
        webTestClient.get().uri("/api/v1/gantt").exchange().expectStatus().isForbidden();
        webTestClient.put()
                .uri("/api/v1/gantt/week-plans")
                .bodyValue(Map.of("projectId", 1, "isoYear", 2026, "isoWeek", 1, "planHours", 1))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void isolation_gantt_does_not_leak_other_user_projects() {
        WebTestClient admin = authedAdminClient();
        Long areaId = createLifeArea(admin, "Работа");
        createProject(admin, areaId, null, "Секрет admin");

        User user2 = new User();
        user2.setUsername("user2");
        user2.setPasswordHash(passwordEncoder.encode("admin"));
        user2.setTimezone("Europe/Moscow");
        user2.setHourAccountingMode("PRIMARY_ONLY");
        user2.setNightStart(LocalTime.of(23, 0));
        user2.setNightEnd(LocalTime.of(7, 0));
        userRepository.save(user2);

        WebTestClient client2 = authedClient("user2", "admin");
        GanttController.GanttResponse gantt = getGantt(client2, "weeks=4");
        assertThat(gantt.getProjects()).isEmpty();
    }

    // --- helpers ---

    private GanttController.GanttResponse getGantt(WebTestClient client, String query) {
        String uri = query == null || query.isBlank() ? "/api/v1/gantt" : "/api/v1/gantt?" + query;
        return client.get()
                .uri(uri)
                .exchange()
                .expectStatus().isOk()
                .expectBody(GanttController.GanttResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private GanttController.ProjectRow findProject(GanttController.GanttResponse gantt, Long id) {
        return gantt.getProjects().stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("project " + id + " not in gantt"));
    }

    private GanttController.CellHours findCell(GanttController.ProjectRow row, int year, int week) {
        return row.getCells().stream()
                .filter(c -> c.getIsoYear() == year && c.getIsoWeek() == week)
                .findFirst()
                .orElseThrow(() -> new AssertionError("cell " + year + "-W" + week + " missing"));
    }

    private Long createLifeArea(WebTestClient client, String name) {
        LifeAreaController.LifeAreaResponse created = client.post()
                .uri("/api/v1/life-areas")
                .bodyValue(Map.of("name", name, "color", "#3d5a4a"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(LifeAreaController.LifeAreaResponse.class)
                .returnResult()
                .getResponseBody();
        return created.getId();
    }

    private ProjectController.ProjectResponse createProject(
            WebTestClient client, Long areaId, Long parentId, String title
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("lifeAreaId", areaId);
        body.put("title", title);
        if (parentId != null) body.put("parentId", parentId);
        return client.post()
                .uri("/api/v1/projects")
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProjectController.ProjectResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private ProjectController.ProjectResponse createProjectWithDates(
            WebTestClient client, Long areaId, Long parentId, String title,
            LocalDate start, LocalDate end
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("lifeAreaId", areaId);
        body.put("title", title);
        body.put("startDate", start.toString());
        body.put("endDate", end.toString());
        if (parentId != null) body.put("parentId", parentId);
        return client.post()
                .uri("/api/v1/projects")
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProjectController.ProjectResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private Long createDelo(WebTestClient client, String title, List<Long> projectIds, Long primary) {
        DeloController.CreateDeloRequest req = new DeloController.CreateDeloRequest();
        req.setTitle(title);
        req.setProjectIds(projectIds);
        req.setPrimaryProjectId(primary);
        req.setExecutionMode(Delo.ExecutionMode.SELF);
        DeloController.DeloResponse created = client.post()
                .uri("/api/v1/delos")
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloController.DeloResponse.class)
                .returnResult()
                .getResponseBody();
        return created.getId();
    }

    private void putEntry(
            WebTestClient client,
            LocalDateTime start,
            LocalDateTime end,
            Long deloId,
            TimeEntry.Status status
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("startAt", start.toString());
        body.put("endAt", end.toString());
        body.put("deloId", deloId);
        body.put("status", status.name());
        client.put()
                .uri("/api/v1/time-entries")
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody(TimeEntryController.TimeEntryResponse.class)
                .returnResult();
    }

    private void putAdHoc(
            WebTestClient client,
            LocalDateTime start,
            LocalDateTime end,
            String text,
            TimeEntry.Status status
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("startAt", start.toString());
        body.put("endAt", end.toString());
        body.put("adHocText", text);
        body.put("status", status.name());
        client.put()
                .uri("/api/v1/time-entries")
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody(TimeEntryController.TimeEntryResponse.class)
                .returnResult();
    }
}
