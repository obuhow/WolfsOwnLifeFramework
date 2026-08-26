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
package ru.wolf.api.routine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.goal.GoalRepository;
import ru.wolf.api.lifesphere.SynergyRepository;
import ru.wolf.api.lifesphere.Synergy;
import ru.wolf.api.lifesphere.SynergyController;
import ru.wolf.api.support.ApiIntegrationTest;

class RoutineApiIT extends ApiIntegrationTest {

    @Autowired
    RoutineRepository routineRepository;

    @Autowired
    RoutineScheduleRepository scheduleRepository;

    @Autowired
    RoutineGoalRepository routineGoalRepository;

    @Autowired
    SynergyRepository synergyRepository;

    @Autowired
    GoalRepository goalRepository;

    @BeforeEach
    void cleanRoutineFixtures() {
        synergyRepository.deleteAll();
        routineGoalRepository.deleteAll();
        scheduleRepository.deleteAll();
        routineRepository.deleteAll();
        goalRepository.deleteAll();
    }

    @Test
    void routine_crud_schedule_goal_link_synergy_and_capacity_quota() {
        WebTestClient authed = authedAdminClient();

        Map<String, Object> goal = authed.post()
                .uri("/api/v1/goals")
                .bodyValue(Map.of("title", "Цель рутины", "priority", 1))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        Long goalId = ((Number) goal.get("id")).longValue();

        RoutineController.RoutineResponse routine = authed.post()
                .uri("/api/v1/routines")
                .bodyValue(Map.of("title", "Сон", "description", "Ночной отдых", "weeklyHours", 56,
                        "color", "#6B7280", "icon", "moon"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(RoutineController.RoutineResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(routine).isNotNull();
        assertThat(routine.getTitle()).isEqualTo("Сон");
        assertThat(routine.getWeeklyHours()).isEqualByComparingTo("56.00");
        assertThat(routine.isArchived()).isFalse();

        RoutineController.ScheduleResponse schedule = authed.post()
                .uri("/api/v1/routines/{id}/schedules", routine.getId())
                .bodyValue(Map.of("dayOfWeek", "MONDAY", "startTime", "09:00", "endTime", "10:30"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(RoutineController.ScheduleResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(schedule.getDayOfWeek()).isEqualTo("MONDAY");

        RoutineController.GoalLinkResponse goalLink = authed.post()
                .uri("/api/v1/routines/{id}/goals/{goalId}", routine.getId(), goalId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(RoutineController.GoalLinkResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(goalLink.getGoalId()).isEqualTo(goalId);

        List<Map> spheres = authed.get()
                .uri("/api/v1/life-spheres")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Map.class)
                .returnResult()
                .getResponseBody();
        Long sphereId = ((Number) spheres.get(0).get("id")).longValue();

        SynergyController.SynergyResponse synergy = authed.post()
                .uri("/api/v1/synergies")
                .bodyValue(Map.of("routineId", routine.getId(), "sphereId", sphereId, "impact", "POSITIVE"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(SynergyController.SynergyResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(synergy.getRoutineId()).isEqualTo(routine.getId());

        List<RoutineController.RoutineResponse> routines = authed.get()
                .uri("/api/v1/routines")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(RoutineController.RoutineResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(routines).anyMatch(item -> item.getId().equals(routine.getId())
                && item.getSchedules().size() == 1
                && item.getGoalIds().contains(goalId));

        List<Map> capacity = authed.get()
                .uri("/api/v1/planning/capacity?from=2026-08-03&to=2026-08-23")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Map.class)
                .returnResult()
                .getResponseBody();
        assertThat(capacity).isNotEmpty();
        assertThat(capacity).allSatisfy(week -> assertThat(((Number) week.get("routineHours")).doubleValue())
                .isEqualTo(56.0));
    }

    @Test
    void routine_rejects_negative_quota_and_invalid_schedule() {
        WebTestClient authed = authedAdminClient();

        authed.post()
                .uri("/api/v1/routines")
                .bodyValue(Map.of("title", "Невалидная", "weeklyHours", -1))
                .exchange()
                .expectStatus().isBadRequest();

        RoutineController.RoutineResponse routine = authed.post()
                .uri("/api/v1/routines")
                .bodyValue(Map.of("title", "Проверка расписания", "weeklyHours", 1))
                .exchange()
                .expectStatus().isOk()
                .expectBody(RoutineController.RoutineResponse.class)
                .returnResult()
                .getResponseBody();

        authed.post()
                .uri("/api/v1/routines/{id}/schedules", routine.getId())
                .bodyValue(Map.of("dayOfWeek", "MONDAY", "startTime", "10:00", "endTime", "09:00"))
                .exchange()
                .expectStatus().isBadRequest();
    }
}
