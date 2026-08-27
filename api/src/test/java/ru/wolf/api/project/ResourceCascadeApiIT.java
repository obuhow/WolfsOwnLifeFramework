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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.goal.GoalProjectRepository;
import ru.wolf.api.goal.GoalRepository;
import ru.wolf.api.goal.GoalWeekBudgetRepository;
import ru.wolf.api.lifearea.LifeAreaController;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.project.dto.*;
import ru.wolf.api.goal.dto.GoalResponse;
import ru.wolf.api.goal.dto.CreateGoalRequest;
import ru.wolf.api.goal.dto.BudgetRequest;
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
        ProjectResponse project = client.post().uri("/api/v1/projects")
                .bodyValue(Map.of(
                        "lifeAreaId", areaId,
                        "title", "Проект каскада",
                        "startDate", LocalDate.now().minusDays(7).toString(),
                        "endDate", oldEnd.toString()))
                .exchange().expectStatus().isOk()
                .expectBody(ProjectResponse.class)
                .returnResult().getResponseBody();
        GoalResponse goal = client.post().uri("/api/v1/goals")
                .bodyValue(new CreateGoalRequest("Цель каскада", null, 1))
                .exchange().expectStatus().isOk()
                .expectBody(GoalResponse.class)
                .returnResult().getResponseBody();
        GoalResponse unrelatedGoal = client.post().uri("/api/v1/goals")
                .bodyValue(new CreateGoalRequest("Другая цель", null, 2))
                .exchange().expectStatus().isOk()
                .expectBody(GoalResponse.class)
                .returnResult().getResponseBody();

        LocalDate affectedWeekDate = oldEnd.plusDays(7);
        String week = "%04d-W%02d".formatted(
                affectedWeekDate.get(WeekFields.ISO.weekBasedYear()),
                affectedWeekDate.get(WeekFields.ISO.weekOfWeekBasedYear()));
        client.post().uri("/api/v1/goals/{id}/budget", goal.id())
                .bodyValue(new BudgetRequest(week, new BigDecimal("20")))
                .exchange().expectStatus().isOk();
        client.post().uri("/api/v1/goals/{id}/budget", unrelatedGoal.id())
                .bodyValue(new BudgetRequest(week, new BigDecimal("20")))
                .exchange().expectStatus().isOk();
        client.post().uri("/api/v1/goals/{id}/projects/{projectId}", goal.id(), project.id())
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
                .uri("/api/v1/projects/{id}/plan-shift-preview", project.id())
                .bodyValue(Map.of("newEnd", oldEnd.plusDays(14).toString()))
                .exchange().expectStatus().isOk()
                .expectBody(ResourceCascadeService.Preview.class)
                .returnResult().getResponseBody();

        assertThat(preview.affectedGoals()).hasSize(1);
        assertThat(preview.affectedGoals().get(0).title()).isEqualTo("Цель каскада");
        assertThat(preview.totalDeficit()).isEqualByComparingTo("10.00");
        assertThat(preview.affectedGoals().get(0).deficit()).isEqualByComparingTo("5.00");

        ProjectDetailResponse unchanged = client.get()
                .uri("/api/v1/projects/{id}", project.id())
                .exchange().expectStatus().isOk()
                .expectBody(ProjectDetailResponse.class)
                .returnResult().getResponseBody();
        assertThat(unchanged.endDate()).isEqualTo(oldEnd);
    }
}
