package ru.wolf.api.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.support.ApiIntegrationTest;

import java.time.LocalTime;

class UserSettingsApiIT extends ApiIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void get_settings_returns_defaults_for_seed_admin() {
        WebTestClient authed = authedAdminClient();

        authed.get()
                .uri("/api/v1/settings")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.timezone").isEqualTo("Europe/Moscow")
                .jsonPath("$.hourAccountingMode").isEqualTo("PRIMARY_ONLY")
                .jsonPath("$.nightStart").isEqualTo("23:00:00")
                .jsonPath("$.nightEnd").isEqualTo("07:00:00");
    }

    @Test
    void update_settings_persists_changes() {
        WebTestClient authed = authedAdminClient();

        var request = new java.util.HashMap<String, Object>();
        request.put("timezone", "Europe/Paris");
        request.put("nightStart", "22:00:00");
        request.put("nightEnd", "06:00:00");
        request.put("hourAccountingMode", "ALL_PROJECTS");

        authed.put()
                .uri("/api/v1/settings")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.timezone").isEqualTo("Europe/Paris")
                .jsonPath("$.hourAccountingMode").isEqualTo("ALL_PROJECTS")
                .jsonPath("$.nightStart").isEqualTo("22:00:00")
                .jsonPath("$.nightEnd").isEqualTo("06:00:00");

        // Verify persisted in DB - check via JSON response instead of raw JDBC to avoid timezone issues
        authed.get()
                .uri("/api/v1/settings")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.timezone").isEqualTo("Europe/Paris")
                .jsonPath("$.hourAccountingMode").isEqualTo("ALL_PROJECTS")
                .jsonPath("$.nightStart").isEqualTo("22:00:00")
                .jsonPath("$.nightEnd").isEqualTo("06:00:00");
    }

    @Test
    void settings_isolation_between_users() {
        // Create second user
        var user2 = new User();
        user2.setUsername("user2");
        user2.setPasswordHash(passwordEncoder.encode("admin"));
        user2.setTimezone("Europe/Moscow");
        user2.setNightStart(LocalTime.of(23, 0));
        user2.setNightEnd(LocalTime.of(7, 0));
        user2.setHourAccountingMode("PRIMARY_ONLY");
        userRepository.save(user2);

        String user2Token = login("user2", "admin");
        WebTestClient user2Client = webTestClient.mutate()
                .defaultHeader("Authorization", "Bearer " + user2Token)
                .build();

        // user2 gets defaults
        user2Client.get()
                .uri("/api/v1/settings")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.timezone").isEqualTo("Europe/Moscow")
                .jsonPath("$.hourAccountingMode").isEqualTo("PRIMARY_ONLY");

        // Admin updates their settings
        var request = new java.util.HashMap<String, Object>();
        request.put("timezone", "Europe/Paris");
        request.put("nightStart", "22:00:00");
        request.put("nightEnd", "06:00:00");
        request.put("hourAccountingMode", "ALL_PROJECTS");

        WebTestClient adminClient = authedAdminClient();
        adminClient.put()
                .uri("/api/v1/settings")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk();

        // user2 settings unchanged
        user2Client.get()
                .uri("/api/v1/settings")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.timezone").isEqualTo("Europe/Moscow")
                .jsonPath("$.hourAccountingMode").isEqualTo("PRIMARY_ONLY");
    }

    @Test
    void unauthenticated_access_rejected() {
        webTestClient.get()
                .uri("/api/v1/settings")
                .exchange()
                .expectStatus().isForbidden();

        webTestClient.put()
                .uri("/api/v1/settings")
                .bodyValue(new java.util.HashMap<>())
                .exchange()
                .expectStatus().isForbidden();
    }
}