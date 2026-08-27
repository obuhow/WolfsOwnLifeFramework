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
package ru.wolf.api.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.auth.DataInitializer;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

/**
 * Release 0.4 ticket 13: closing access to the seed {@code admin/admin} account once a named
 * administrator exists, without deleting {@code admin} or its historical data.
 *
 * <p>The seed {@code admin} row is shared with every other IT class in the suite (seeded once by
 * {@link DataInitializer} against the shared Testcontainers context), so each test here restores
 * it to a known ACTIVE state with the original password before mutating it, instead of assuming
 * test execution order.
 */
class AdminApiIT extends ApiIntegrationTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    DataInitializer dataInitializer;

    @BeforeEach
    void resetSeedAdmin() {
        User seedAdmin = userRepository.findByUsername("admin").orElseThrow();
        seedAdmin.setStatus("ACTIVE");
        seedAdmin.setPasswordHash(passwordEncoder.encode("admin"));
        userRepository.save(seedAdmin);
    }

    private User createAdmin(String username) {
        User admin = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode("pass1234"))
                .role("ADMIN")
                .status("ACTIVE")
                .accountType("REGULAR")
                .build();
        return userRepository.save(admin);
    }

    @Test
    void named_admin_can_block_seed_admin_and_login_is_rejected() {
        User namedAdmin = createAdmin("obuhov-test");
        WebTestClient namedAdminClient = authedClient("obuhov-test", "pass1234");

        User seedAdmin = userRepository.findByUsername("admin").orElseThrow();

        namedAdminClient.post()
                .uri("/api/v1/admin/users/{id}/block", seedAdmin.getId())
                .exchange()
                .expectStatus().isOk();

        var body = new java.util.HashMap<String, String>();
        body.put("username", "admin");
        body.put("password", "admin");

        webTestClient.post()
                .uri("/api/v1/auth/login")
                .bodyValue(body)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void blocked_seed_admin_existing_token_rejected_on_protected_endpoint() {
        createAdmin("obuhov-test2");
        WebTestClient namedAdminClient = authedClient("obuhov-test2", "pass1234");

        // Token issued while admin is still active.
        String preBlockToken = login("admin", "admin");

        User seedAdmin = userRepository.findByUsername("admin").orElseThrow();
        namedAdminClient.post()
                .uri("/api/v1/admin/users/{id}/block", seedAdmin.getId())
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri("/api/v1/projects")
                .header("Authorization", "Bearer " + preBlockToken)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void data_initializer_does_not_unblock_existing_seed_admin() {
        User seedAdmin = userRepository.findByUsername("admin").orElseThrow();
        seedAdmin.setStatus("BLOCKED");
        userRepository.save(seedAdmin);

        // Re-run the same startup logic that executes on every container restart.
        dataInitializer.run();

        User reloaded = userRepository.findByUsername("admin").orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo("BLOCKED");
    }

    @Test
    void regular_user_gets_403_on_admin_endpoints() {
        User user = User.builder()
                .username("regular-user-test")
                .passwordHash(passwordEncoder.encode("pass1234"))
                .role("USER")
                .status("ACTIVE")
                .accountType("REGULAR")
                .build();
        userRepository.save(user);
        WebTestClient regularClient = authedClient("regular-user-test", "pass1234");

        regularClient.get()
                .uri("/api/v1/admin/users")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void reset_password_on_seed_admin_replaces_password_hash() {
        createAdmin("obuhov-test3");
        WebTestClient namedAdminClient = authedClient("obuhov-test3", "pass1234");

        User seedAdmin = userRepository.findByUsername("admin").orElseThrow();
        String oldHash = seedAdmin.getPasswordHash();

        namedAdminClient.post()
                .uri("/api/v1/admin/users/{id}/reset-password", seedAdmin.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.tempPassword").isNotEmpty();

        User reloaded = userRepository.findByUsername("admin").orElseThrow();
        assertThat(reloaded.getPasswordHash()).isNotEqualTo(oldHash);
    }
}
