package ru.wolf.api.project;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.goal.GoalController;
import ru.wolf.api.goal.GoalProjectRepository;
import ru.wolf.api.goal.GoalRepository;
import ru.wolf.api.goal.GoalWeekBudgetRepository;
import ru.wolf.api.lifearea.LifeAreaController;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.user.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceCascadeApiIT extends ApiIntegrationTest {

    @Autowired GoalProjectRepository goalProjectRepository;
    @Autowired GoalWeekBudgetRepository goalWeekBudgetRepository;
    @Autowired GoalRepository goalRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired LifeAreaRepository lifeAreaRepository;
    @Autowired UserRepository userRepository;

    @BeforeEach
    void cleanup() {
        goalWeekBudgetRepository.deleteAll();
        goalProjectRepository.deleteAll();
        goalRepository.deleteAll();
        projectRepository.deleteAll();
        lifeAreaRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(user -> !"admin".equals(user.getUsername()))
                .forEach(userRepository::delete);
        userRepository.findByUsername("admin").ifPresent(user -> {
            user.setAvailableWeeklyHours(new BigDecimal("30"));
            userRepository.save(user);
        });
    }

    @Test
    void plan_shift_preview_reports_linked_goal_deficit_without_changing_project() {
        WebTestClient client = authedAdminClient();
        Long areaId = client.post().uri("/api/v1/life-areas")
                .bodyValue(new LifeAreaController.CreateLifeAreaRequest("Работа", "#123456"))
                .exchange().expectStatus().isOk()
                .expectBody(LifeAreaController.LifeAreaResponse.class)
                .returnResult().getResponseBody().getId();
        LocalDate oldEnd = LocalDate.now().plusDays(7);
        ProjectController.ProjectResponse project = client.post().uri("/api/v1/projects")
                .bodyValue(Map.of(
                        "lifeAreaId", areaId,
                        "title", "Проект каскада",
                        "startDate", LocalDate.now().minusDays(7).toString(),
                        "endDate", oldEnd.toString()))
                .exchange().expectStatus().isOk()
                .expectBody(ProjectController.ProjectResponse.class)
                .returnResult().getResponseBody();
        GoalController.GoalResponse goal = client.post().uri("/api/v1/goals")
                .bodyValue(new GoalController.CreateGoalRequest("Цель каскада", null, 1))
                .exchange().expectStatus().isOk()
                .expectBody(GoalController.GoalResponse.class)
                .returnResult().getResponseBody();
        GoalController.GoalResponse unrelatedGoal = client.post().uri("/api/v1/goals")
                .bodyValue(new GoalController.CreateGoalRequest("Другая цель", null, 2))
                .exchange().expectStatus().isOk()
                .expectBody(GoalController.GoalResponse.class)
                .returnResult().getResponseBody();

        String week = "%04d-W%02d".formatted(
                LocalDate.now().get(WeekFields.ISO.weekBasedYear()),
                LocalDate.now().get(WeekFields.ISO.weekOfWeekBasedYear()));
        client.post().uri("/api/v1/goals/{id}/budget", goal.getId())
                .bodyValue(new GoalController.BudgetRequest(week, new BigDecimal("20")))
                .exchange().expectStatus().isOk();
        client.post().uri("/api/v1/goals/{id}/budget", unrelatedGoal.getId())
                .bodyValue(new GoalController.BudgetRequest(week, new BigDecimal("20")))
                .exchange().expectStatus().isOk();
        client.post().uri("/api/v1/goals/{id}/projects/{projectId}", goal.getId(), project.getId())
                .exchange().expectStatus().isNoContent();
        client.put().uri("/api/v1/settings")
                .bodyValue(Map.of(
                        "timezone", "Europe/Moscow",
                        "nightStart", "23:00:00",
                        "nightEnd", "07:00:00",
                        "hourAccountingMode", "PRIMARY_ONLY",
                        "availableWeeklyHours", 30))
                .exchange().expectStatus().isOk();

        ResourceCascadeService.Preview preview = client.post()
                .uri("/api/v1/projects/{id}/plan-shift-preview", project.getId())
                .bodyValue(Map.of("newEnd", oldEnd.plusDays(14).toString()))
                .exchange().expectStatus().isOk()
                .expectBody(ResourceCascadeService.Preview.class)
                .returnResult().getResponseBody();

        assertThat(preview.affectedGoals()).hasSize(1);
        assertThat(preview.affectedGoals().get(0).title()).isEqualTo("Цель каскада");
        assertThat(preview.totalDeficit()).isEqualByComparingTo("10.00");
        assertThat(preview.affectedGoals().get(0).deficit()).isEqualByComparingTo("5.00");

        ProjectController.ProjectDetailResponse unchanged = client.get()
                .uri("/api/v1/projects/{id}", project.getId())
                .exchange().expectStatus().isOk()
                .expectBody(ProjectController.ProjectDetailResponse.class)
                .returnResult().getResponseBody();
        assertThat(unchanged.getEndDate()).isEqualTo(oldEnd);
    }
}
