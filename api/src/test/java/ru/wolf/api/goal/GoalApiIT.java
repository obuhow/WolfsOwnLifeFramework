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
package ru.wolf.api.goal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.delo.DeloController;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.goal.dto.BudgetRequest;
import ru.wolf.api.goal.dto.BudgetResponse;
import ru.wolf.api.goal.dto.CreateGoalRequest;
import ru.wolf.api.goal.dto.FactResponse;
import ru.wolf.api.goal.dto.GoalDetailResponse;
import ru.wolf.api.goal.dto.GoalResponse;
import ru.wolf.api.goal.dto.MetricRequest;
import ru.wolf.api.goal.dto.PriorityRequest;
import ru.wolf.api.lifearea.dto.*;
import ru.wolf.api.goal.GoalRepository;
import ru.wolf.api.goal.GoalMetricRepository;
import ru.wolf.api.goal.GoalWeekBudgetRepository;
import ru.wolf.api.goal.GoalProjectRepository;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.project.dto.CreateProjectRequest;
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
        GoalResponse goal = createGoal(client, "ISTQB", 1);

        client.post().uri("/api/v1/goals/{id}/budget", goal.id())
                .bodyValue(new BudgetRequest("2026-W11", new BigDecimal("10")))
                .exchange().expectStatus().isOk()
                .expectBody(BudgetResponse.class)
                .value(budget -> assertThat(budget.hours()).isEqualByComparingTo("10.00"));

        client.post().uri("/api/v1/goals/{id}/metrics", goal.id())
                .bodyValue(new MetricRequest("projects", new BigDecimal("5"), LocalDateTime.of(2026, 3, 10, 12, 0)))
                .exchange().expectStatus().isOk();

        client.post().uri("/api/v1/goals/{id}/projects/{projectId}", goal.id(), projectId)
                .exchange().expectStatus().isNoContent();

        GoalDetailResponse detail = client.get()
                .uri(uri -> uri.path("/api/v1/goals/{id}").queryParam("week", "2026-W11").build(goal.id()))
                .exchange().expectStatus().isOk()
                .expectBody(GoalDetailResponse.class).returnResult().getResponseBody();

        assertThat(detail).isNotNull();
        assertThat(detail.goal().title()).isEqualTo("ISTQB");
        assertThat(detail.projects()).extracting(ru.wolf.api.goal.dto.ProjectResponse::id).containsExactly(projectId);
        assertThat(detail.metrics()).hasSize(1);
        assertThat(detail.budget().hours()).isEqualByComparingTo("10.00");
    }

    @Test
    void duplicate_priority_returns_conflict_and_reorder_changes_order() {
        WebTestClient client = authedAdminClient();
        GoalResponse first = createGoal(client, "Первая", 1);

        client.post().uri("/api/v1/goals")
                .bodyValue(new CreateGoalRequest("Дубликат", null, 1))
                .exchange().expectStatus().isEqualTo(409);

        GoalResponse second = createGoal(client, "Вторая", 2);
        client.put().uri("/api/v1/goals/{id}/priority", second.id())
                .bodyValue(new PriorityRequest(1))
                .exchange().expectStatus().isOk();

        List<GoalResponse> goals = client.get().uri("/api/v1/goals")
                .exchange().expectStatus().isOk()
                .expectBodyList(GoalResponse.class).returnResult().getResponseBody();
        assertThat(goals).extracting(GoalResponse::id)
                .containsExactly(second.id(), first.id());
        assertThat(goals).extracting(GoalResponse::priority)
                .containsExactly(1, 2);
    }

    @Test
    void fact_counts_done_hours_once_for_goal_with_multiple_projects() {
        WebTestClient client = authedAdminClient();
        Long p1 = createProject(client, "Работа", "Основной");
        Long p2 = createProject(client, "Музыка", "Второй");
        Long deloId = createDelo(client, "Код", List.of(p1, p2), p1);
        putDoneEntry(client, deloId, "2026-03-10T10:00:00", "2026-03-10T12:30:00");
        GoalResponse goal = createGoal(client, "Релиз", 1);
        linkProject(client, goal.id(), p1);
        linkProject(client, goal.id(), p2);

        FactResponse primary = fact(client, goal.id());
        assertThat(primary.hours()).isEqualByComparingTo("2.50");

        User admin = userRepository.findByUsername("admin").orElseThrow();
        admin.setHourAccountingMode("ALL_PROJECTS");
        userRepository.save(admin);

        FactResponse allProjects = fact(client, goal.id());
        assertThat(allProjects.hours()).isEqualByComparingTo("2.50");
    }

    private FactResponse fact(WebTestClient client, Long goalId) {
        return client.get().uri(uri -> uri.path("/api/v1/goals/{id}/fact")
                        .queryParam("week", "2026-W11").build(goalId))
                .exchange().expectStatus().isOk()
                .expectBody(FactResponse.class).returnResult().getResponseBody();
    }

    private GoalResponse createGoal(WebTestClient client, String title, Integer priority) {
        return client.post().uri("/api/v1/goals")
                .bodyValue(new CreateGoalRequest(title, null, priority))
                .exchange().expectStatus().isOk()
                .expectBody(GoalResponse.class).returnResult().getResponseBody();
    }

    private void linkProject(WebTestClient client, Long goalId, Long projectId) {
        client.post().uri("/api/v1/goals/{id}/projects/{projectId}", goalId, projectId)
                .exchange().expectStatus().isNoContent();
    }

    private Long createProject(WebTestClient client, String areaName, String title) {
        Long areaId = client.post().uri("/api/v1/life-areas")
                .bodyValue(new CreateLifeAreaRequest(areaName, "#123456"))
                .exchange().expectStatus().isOk()
                .expectBody(LifeAreaResponse.class).returnResult().getResponseBody().id();
        CreateProjectRequest request = new CreateProjectRequest(areaId, null, title, null, null, null, null, null, null);
        return client.post().uri("/api/v1/projects").bodyValue(request)
                .exchange().expectStatus().isOk()
                .expectBody(ru.wolf.api.project.dto.ProjectResponse.class).returnResult().getResponseBody().id();
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
