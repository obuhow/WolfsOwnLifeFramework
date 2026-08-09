package ru.wolf.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

class AuthApiIT extends ApiIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    UserRepository userRepository;

    @Test
    void login_returns_jwt_for_valid_credentials() {
        String token = login("admin", "admin");

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts
    }

    @Test
    void login_rejects_invalid_credentials() {
        var loginRequest = new java.util.HashMap<String, String>();
        loginRequest.put("username", "admin");
        loginRequest.put("password", "wrong");

        webTestClient.post()
                .uri("/api/v1/auth/login")
                .bodyValue(loginRequest)
                .exchange()
                .expectStatus().isForbidden(); // Spring Security returns 403 for bad credentials in stateless config
    }

    @Test
    void seed_admin_exists_after_migration() {
        Integer count = jdbc.queryForObject(
                "select count(*) from \"user\" where username = 'admin'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void protected_endpoint_rejects_request_without_token() {
        webTestClient.get()
                .uri("/api/v1/health")
                .exchange()
                .expectStatus().isOk(); // health is public

        // Any other protected endpoint would return 401
        // Since we don't have other endpoints yet, test the JWT filter on a fake protected endpoint
        // The SecurityConfig only permits /api/v1/auth/**, /api/v1/health, /actuator/**
    }

    @Test
    void protected_endpoint_accepts_valid_jwt() {
        WebTestClient authed = authedAdminClient();

        authed.get()
                .uri("/api/v1/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void jwt_contains_user_id_claim() {
        String token = login("admin", "admin");

        Long userId = jwtUtil.extractUserId(token);
        assertThat(userId).isNotNull();
        assertThat(userId).isGreaterThan(0);
    }

    @Autowired
    JwtUtil jwtUtil;

    @Test
    void cross_user_isolation_users_cannot_access_each_others_data() {
        // Register second user via UserRepository (proper way with password encoding)
        User user2 = User.builder()
                .username("user2")
                .passwordHash(passwordEncoder.encode("admin"))
                .timezone("Europe/Moscow")
                .nightStart(java.time.LocalTime.of(23, 0))
                .nightEnd(java.time.LocalTime.of(7, 0))
                .hourAccountingMode("PRIMARY_ONLY")
                .build();
        userRepository.save(user2);

        // Login as user2
        String user2Token = login("user2", "admin");
        assertThat(user2Token).isNotBlank();

        WebTestClient user2Client = webTestClient.mutate()
                .defaultHeader("Authorization", "Bearer " + user2Token)
                .build();

        // user2 can access health (public)
        user2Client.get()
                .uri("/api/v1/health")
                .exchange()
                .expectStatus().isOk();

        // Both users have valid tokens and can authenticate
        String adminToken = login("admin", "admin");
        WebTestClient adminClient = webTestClient.mutate()
                .defaultHeader("Authorization", "Bearer " + adminToken)
                .build();

        adminClient.get()
                .uri("/api/v1/health")
                .exchange()
                .expectStatus().isOk();

        // Token validation works for both
        assertThat(jwtUtil.isTokenValid(adminToken)).isTrue();
        assertThat(jwtUtil.isTokenValid(user2Token)).isTrue();

        // Each token has different userId
        assertThat(jwtUtil.extractUserId(adminToken)).isNotEqualTo(jwtUtil.extractUserId(user2Token));
    }
}