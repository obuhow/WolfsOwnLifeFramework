package ru.wolf.api.goal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.delo.DeloController;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.lifearea.LifeAreaController;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.project.ProjectController;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GoalApiIT extends ApiIntegrationTest {

    @Autowired GoalRepository goalRepository;
    @Autowired GoalMetricRepository metricRepository;
    @Autowired GoalWeekBudgetRepository budgetRepository;
    @Autowired GoalProjectRepository goalProjectRepository;
    @Autowired TimeEntryRepository timeEntryRepository;
    @Autowired DeloProjectRepository deloProjectRepository;
    @Autowired DeloRepository deloRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired LifeAreaRepository lifeAreaRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanup() {
        metricRepository.deleteAll();
        budgetRepository.deleteAll();
        goalProjectRepository.deleteAll();
        goalRepository.deleteAll();
        timeEntryRepository.deleteAll();
        deloProjectRepository.deleteAll();
        deloRepository.deleteAll();
        projectRepository.deleteAll();
        lifeAreaRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> !"admin".equals(u.getUsername()))
                .forEach(userRepository::delete);
        User admin = userRepository.findByUsername("admin").orElseThrow();
        admin.setHourAccountingMode("PRIMARY_ONLY");
        admin.setDayEnd(LocalTime.MIDNIGHT);
        userRepository.save(admin);
    }

    @Test
    void create_goal_budget_metric_and_project_link() {
        WebTestClient client = authedAdminClient();
        Long projectId = createProject(client, "Работа", "WOLF");
        GoalController.GoalResponse goal = createGoal(client, "ISTQB", 1);

        client.post().uri("/api/v1/goals/{id}/budget", goal.getId())
                .bodyValue(new GoalController.BudgetRequest("2026-W11", new BigDecimal("10")))
                .exchange().expectStatus().isOk()
                .expectBody(GoalController.BudgetResponse.class)
                .value(budget -> assertThat(budget.getHours()).isEqualByComparingTo("10.00"));

        client.post().uri("/api/v1/goals/{id}/metrics", goal.getId())
                .bodyValue(new GoalController.MetricRequest("projects", new BigDecimal("5"), LocalDateTime.of(2026, 3, 10, 12, 0)))
                .exchange().expectStatus().isOk();

        client.post().uri("/api/v1/goals/{id}/projects/{projectId}", goal.getId(), projectId)
                .exchange().expectStatus().isNoContent();

        GoalController.GoalDetailResponse detail = client.get()
                .uri(uri -> uri.path("/api/v1/goals/{id}").queryParam("week", "2026-W11").build(goal.getId()))
                .exchange().expectStatus().isOk()
                .expectBody(GoalController.GoalDetailResponse.class).returnResult().getResponseBody();

        assertThat(detail).isNotNull();
        assertThat(detail.getGoal().getTitle()).isEqualTo("ISTQB");
        assertThat(detail.getProjects()).extracting(GoalController.ProjectResponse::getId).containsExactly(projectId);
        assertThat(detail.getMetrics()).hasSize(1);
        assertThat(detail.getBudget().getHours()).isEqualByComparingTo("10.00");
    }

    @Test
    void duplicate_priority_returns_conflict_and_reorder_changes_order() {
        WebTestClient client = authedAdminClient();
        GoalController.GoalResponse first = createGoal(client, "Первая", 1);

        client.post().uri("/api/v1/goals")
                .bodyValue(new GoalController.CreateGoalRequest("Дубликат", null, 1))
                .exchange().expectStatus().isEqualTo(409);

        GoalController.GoalResponse second = createGoal(client, "Вторая", 2);
        client.put().uri("/api/v1/goals/{id}/priority", second.getId())
                .bodyValue(new GoalController.PriorityRequest(1))
                .exchange().expectStatus().isOk();

        List<GoalController.GoalResponse> goals = client.get().uri("/api/v1/goals")
                .exchange().expectStatus().isOk()
                .expectBodyList(GoalController.GoalResponse.class).returnResult().getResponseBody();
        assertThat(goals).extracting(GoalController.GoalResponse::getId)
                .containsExactly(second.getId(), first.getId());
        assertThat(goals).extracting(GoalController.GoalResponse::getPriority)
                .containsExactly(1, 2);
    }

    @Test
    void fact_counts_done_hours_once_for_goal_with_multiple_projects() {
        WebTestClient client = authedAdminClient();
        Long p1 = createProject(client, "Работа", "Основной");
        Long p2 = createProject(client, "Музыка", "Второй");
        Long deloId = createDelo(client, "Код", List.of(p1, p2), p1);
        putDoneEntry(client, deloId, "2026-03-10T10:00:00", "2026-03-10T12:30:00");
        GoalController.GoalResponse goal = createGoal(client, "Релиз", 1);
        linkProject(client, goal.getId(), p1);
        linkProject(client, goal.getId(), p2);

        GoalController.FactResponse primary = fact(client, goal.getId());
        assertThat(primary.getHours()).isEqualByComparingTo("2.50");

        User admin = userRepository.findByUsername("admin").orElseThrow();
        admin.setHourAccountingMode("ALL_PROJECTS");
        userRepository.save(admin);

        GoalController.FactResponse allProjects = fact(client, goal.getId());
        assertThat(allProjects.getHours()).isEqualByComparingTo("2.50");
    }

    private GoalController.FactResponse fact(WebTestClient client, Long goalId) {
        return client.get().uri(uri -> uri.path("/api/v1/goals/{id}/fact")
                        .queryParam("week", "2026-W11").build(goalId))
                .exchange().expectStatus().isOk()
                .expectBody(GoalController.FactResponse.class).returnResult().getResponseBody();
    }

    private GoalController.GoalResponse createGoal(WebTestClient client, String title, Integer priority) {
        return client.post().uri("/api/v1/goals")
                .bodyValue(new GoalController.CreateGoalRequest(title, null, priority))
                .exchange().expectStatus().isOk()
                .expectBody(GoalController.GoalResponse.class).returnResult().getResponseBody();
    }

    private void linkProject(WebTestClient client, Long goalId, Long projectId) {
        client.post().uri("/api/v1/goals/{id}/projects/{projectId}", goalId, projectId)
                .exchange().expectStatus().isNoContent();
    }

    private Long createProject(WebTestClient client, String areaName, String title) {
        Long areaId = client.post().uri("/api/v1/life-areas")
                .bodyValue(new LifeAreaController.CreateLifeAreaRequest(areaName, "#123456"))
                .exchange().expectStatus().isOk()
                .expectBody(LifeAreaController.LifeAreaResponse.class).returnResult().getResponseBody().getId();
        ProjectController.CreateProjectRequest request = new ProjectController.CreateProjectRequest();
        request.setLifeAreaId(areaId);
        request.setTitle(title);
        return client.post().uri("/api/v1/projects").bodyValue(request)
                .exchange().expectStatus().isOk()
                .expectBody(ProjectController.ProjectResponse.class).returnResult().getResponseBody().getId();
    }

    private Long createDelo(WebTestClient client, String title, List<Long> projects, Long primary) {
        DeloController.CreateDeloRequest request = new DeloController.CreateDeloRequest();
        request.setTitle(title);
        request.setProjectIds(projects);
        request.setPrimaryProjectId(primary);
        return client.post().uri("/api/v1/delos").bodyValue(request)
                .exchange().expectStatus().isOk()
                .expectBody(DeloController.DeloResponse.class).returnResult().getResponseBody().getId();
    }

    private void putDoneEntry(WebTestClient client, Long deloId, String start, String end) {
        client.put().uri("/api/v1/time-entries")
                .bodyValue(Map.of("startAt", start, "endAt", end, "deloId", deloId, "status", "DONE"))
                .exchange().expectStatus().isOk();
    }
}
