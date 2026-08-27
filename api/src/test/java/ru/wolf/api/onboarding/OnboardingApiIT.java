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
package ru.wolf.api.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.goal.GoalRepository;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.onboarding.dto.Step1Request;
import ru.wolf.api.onboarding.dto.Step1Response;
import ru.wolf.api.onboarding.dto.Step2Request;
import ru.wolf.api.onboarding.dto.Step2Response;
import ru.wolf.api.onboarding.dto.Step3Request;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.math.BigDecimal;

/**
 * Coverage for the checklist item left open by ticket
 * {@code .scratch/release-0.4-multiuser-demo/issues/08-first-run-wizard.md}:
 *
 * <blockquote>{@code OnboardingApiIT}: флаг проставляется; повторный вход не редиректит;
 * пропуск не создаёт сущностей</blockquote>
 *
 * <p>Recorded as missing in bug
 * {@code .scratch/release-0.6-demo-onboarding/bugs/01-first-run-wizard-missing-ui.md}.
 * The frontend wizard/route ({@code OnboardingWizardView.vue}, {@code /onboarding}) was
 * already shipped in release 0.6 ticket 04; this test only closes the server-side
 * assertions the original ticket's checklist called for and never got.
 */
class OnboardingApiIT extends ApiIntegrationTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    ProjectRepository projectRepository;

    @Autowired
    GoalRepository goalRepository;

    @Test
    void complete_sets_onboarding_flag_and_is_reflected_in_me() {
        WebTestClient authed = authedAdminClient();

        // Precondition: seed admin already has onboarding completed (DataInitializer).
        // Exercise a fresh user instead so the flag transition is observable.
        User user2 = newUser("wizard-user");
        WebTestClient user2Client = clientFor(user2);

        user2Client.get()
                .uri("/api/v1/auth/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.onboardingCompleted").isEqualTo(false);

        user2Client.post()
                .uri("/api/v1/onboarding/complete")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.completedAt").exists();

        user2Client.get()
                .uri("/api/v1/auth/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.onboardingCompleted").isEqualTo(true);

        assertThat(userRepository.findByUsername("wizard-user").orElseThrow()
                .getOnboardingCompletedAt()).isNotNull();

        // authed(admin) unaffected by user2's completion
        authed.get()
                .uri("/api/v1/auth/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.onboardingCompleted").isEqualTo(true);
    }

    @Test
    void repeat_login_after_completion_does_not_redirect_to_onboarding() {
        // The frontend guard (main.js) treats onboardingCompleted from /auth/me as the
        // sole redirect signal. Once completed, subsequent status checks must keep
        // reporting completed=true so the guard never sends the user back to /onboarding.
        User user2 = newUser("repeat-login-user");
        WebTestClient user2Client = clientFor(user2);

        user2Client.post().uri("/api/v1/onboarding/complete").exchange().expectStatus().isOk();

        // Simulate a second, independent login/session.
        String secondToken = login("repeat-login-user", "admin");
        WebTestClient secondSession = webTestClient.mutate()
                .defaultHeader("Authorization", "Bearer " + secondToken)
                .build();

        secondSession.get()
                .uri("/api/v1/onboarding/status")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.onboardingCompleted").isEqualTo(true);

        secondSession.get()
                .uri("/api/v1/auth/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.onboardingCompleted").isEqualTo(true);
    }

    @Test
    void skip_completes_onboarding_without_creating_project_or_goal() {
        User user2 = newUser("skip-user");
        WebTestClient user2Client = clientFor(user2);

        long projectsBefore = projectRepository.findByUserOrderByTitleAsc(user2).size();
        long goalsBefore = goalRepository.findByUserAndArchivedOrderByPriorityAsc(user2, false).size();

        user2Client.post()
                .uri("/api/v1/onboarding/skip")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true);

        assertThat(userRepository.findByUsername("skip-user").orElseThrow()
                .getOnboardingCompletedAt()).isNotNull();

        assertThat(projectRepository.findByUserOrderByTitleAsc(user2)).hasSize((int) projectsBefore);
        assertThat(goalRepository.findByUserAndArchivedOrderByPriorityAsc(user2, false))
                .hasSize((int) goalsBefore);

        user2Client.get()
                .uri("/api/v1/auth/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.onboardingCompleted").isEqualTo(true);
    }

    @Test
    void full_wizard_creates_project_and_goal_then_completes() {
        User user2 = newUser("full-wizard-user");
        WebTestClient user2Client = clientFor(user2);

        var step1 = new Step1Request("Мой первый проект");
        Step1Response projectResp = user2Client.post()
                .uri("/api/v1/onboarding/step1/project")
                .bodyValue(step1)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Step1Response.class)
                .returnResult()
                .getResponseBody();
        assertThat(projectResp).isNotNull();
        assertThat(projectResp.projectId()).isNotNull();

        var step2 = new Step2Request(projectResp.projectId(), "Моя первая цель", new BigDecimal("5"));
        Step2Response goalResp = user2Client.post()
                .uri("/api/v1/onboarding/step2/goal")
                .bodyValue(step2)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Step2Response.class)
                .returnResult()
                .getResponseBody();
        assertThat(goalResp).isNotNull();
        assertThat(goalResp.goalId()).isNotNull();

        var step3 = new Step3Request(new BigDecimal("12"));
        user2Client.post()
                .uri("/api/v1/onboarding/step3/weekly-hours")
                .bodyValue(step3)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.availableWeeklyHours").isEqualTo(12.00);

        user2Client.post()
                .uri("/api/v1/onboarding/complete")
                .exchange()
                .expectStatus().isOk();

        assertThat(projectRepository.findByUserOrderByTitleAsc(user2)).hasSize(1);
        assertThat(goalRepository.findByUserAndArchivedOrderByPriorityAsc(user2, false)).hasSize(1);
        assertThat(userRepository.findByUsername("full-wizard-user").orElseThrow()
                .getOnboardingCompletedAt()).isNotNull();
    }

    // --- helpers ---

    private User newUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder().encode("admin"));
        user.setTimezone("Europe/Moscow");
        user.setNightStart(java.time.LocalTime.of(23, 0));
        user.setNightEnd(java.time.LocalTime.of(7, 0));
        user.setHourAccountingMode("PRIMARY_ONLY");
        user.setOnboardingCompletedAt(null);
        return userRepository.save(user);
    }

    private WebTestClient clientFor(User user) {
        String token = login(user.getUsername(), "admin");
        return webTestClient.mutate()
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
    }

    @Autowired
    org.springframework.security.crypto.password.PasswordEncoder passwordEncoderBean;

    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return passwordEncoderBean;
    }
}
