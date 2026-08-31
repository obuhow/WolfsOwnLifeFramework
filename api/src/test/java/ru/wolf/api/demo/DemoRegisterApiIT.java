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
package ru.wolf.api.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.wolf.api.admin.dto.DeleteUserRequest;
import ru.wolf.api.admin.dto.UserAdminResponse;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserPurgeService;
import ru.wolf.api.user.UserRepository;

/**
 * Release 1.0, ticket 07: anonymous demo registration creates an isolated pre-filled DEMO account,
 * returns credentials plus JWT, allocates A1/B1 slots, and lets an administrator remove the account
 * together with its fixture data.
 */
class DemoRegisterApiIT extends ApiIntegrationTest {

    @Autowired UserRepository userRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired DeloRepository deloRepository;
    @Autowired UserPurgeService userPurgeService;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager entityManager;

    @BeforeEach
    void removeDemoAccounts() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.clear();
            userRepository.findAll().stream()
                    .filter(user -> "DEMO".equals(user.getAccountType()))
                    .forEach(user -> {
                        entityManager.clear();
                        User freshUser = userRepository.findById(user.getId()).orElseThrow();
                        userPurgeService.purgeProfileData(freshUser);
                        entityManager.clear();
                        userRepository.deleteById(freshUser.getId());
                        entityManager.clear();
                    });
            entityManager.clear();
        });
    }

    @Test
    void anonymous_registration_creates_prefilled_demo_and_admin_can_delete_it() {
        webTestClient.get()
                .uri("/api/v1/demo/profiles")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Map.class)
                .hasSize(3);

        Map<String, String> response = webTestClient.post()
                .uri("/api/v1/demo/register")
                .bodyValue(Map.of("profileSlug", "worker-class"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        String username = response.get("username");
        assertThat(username).matches("GoodEnoughDemoUser-[A-Z]1");
        assertThat(response.get("password")).isEqualTo(username);
        String token = response.get("token");
        assertThat(token).isNotBlank();
        assertThat(response.get("profileDisplayName")).isEqualTo("Рабочий класс");

        webTestClient.get()
                .uri("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.username").isEqualTo(username)
                .jsonPath("$.accountType").isEqualTo("DEMO")
                .jsonPath("$.onboardingCompleted").isEqualTo(true);

        webTestClient.post()
                .uri("/api/v1/auth/login")
                .bodyValue(Map.of("username", username, "password", username))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isNotEmpty();

        User demo = userRepository.findByUsername(username).orElseThrow();
        assertThat(demo.getAccountType()).isEqualTo("DEMO");
        assertThat(demo.getRole()).isEqualTo("USER");
        assertThat(demo.getStatus()).isEqualTo("ACTIVE");
        assertThat(demo.getOnboardingCompletedAt()).isNotNull();
        assertThat(projectRepository.findByUserOrderByTitleAsc(demo)).isNotEmpty();
        assertThat(deloRepository.findByUserOrderByTitleAsc(demo)).isNotEmpty();

        List<UserAdminResponse> adminUsers = authedAdminClient().get()
                .uri("/api/v1/admin/users?includeDemo=true")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(UserAdminResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(adminUsers).anyMatch(user -> username.equals(user.username())
                && "DEMO".equals(user.accountType()));

        authedAdminClient().method(org.springframework.http.HttpMethod.DELETE)
                .uri("/api/v1/admin/users/{id}", demo.getId())
                .bodyValue(new DeleteUserRequest(username))
                .exchange()
                .expectStatus().isNoContent();

        assertThat(userRepository.findById(demo.getId())).isEmpty();
        assertThat(projectRepository.findByUserOrderByTitleAsc(demo)).isEmpty();
        assertThat(deloRepository.findByUserOrderByTitleAsc(demo)).isEmpty();
    }

    @Test
    void repeated_registration_allocates_next_demo_slot() {
        String first = register("wise-freelancer");
        String second = register("free-artist");

        assertThat(first).isEqualTo("GoodEnoughDemoUser-A1");
        assertThat(second).isEqualTo("GoodEnoughDemoUser-B1");
    }

    @Test
    void invalid_profile_is_rejected_without_creating_account() {
        long before = userRepository.findAll().stream()
                .filter(user -> "DEMO".equals(user.getAccountType()))
                .count();

        webTestClient.post()
                .uri("/api/v1/demo/register")
                .bodyValue(Map.of("profileSlug", "unknown"))
                .exchange()
                .expectStatus().isBadRequest();

        assertThat(userRepository.findAll().stream()
                .filter(user -> "DEMO".equals(user.getAccountType()))).hasSize((int) before);
    }

    private String register(String slug) {
        Map<String, String> response = webTestClient.post()
                .uri("/api/v1/demo/register")
                .bodyValue(Map.of("profileSlug", slug))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        assertThat(response).isNotNull();
        return response.get("username");
    }
}
