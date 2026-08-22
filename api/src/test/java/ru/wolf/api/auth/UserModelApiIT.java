package ru.wolf.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

/**
 * Release 0.4 ticket 01: role/status/account_type/expires_at on User, JWT role claim,
 * blocked/expired access denial.
 */
class UserModelApiIT extends ApiIntegrationTest {

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtUtil jwtUtil;

    @Test
    void admin_seeded_with_admin_role() {
        User admin = userRepository.findByUsername("admin").orElseThrow();
        assertThat(admin.getRole()).isEqualTo("ADMIN");
        assertThat(admin.getStatus()).isEqualTo("ACTIVE");
        assertThat(admin.getAccountType()).isEqualTo("REGULAR");
        assertThat(admin.getOnboardingCompletedAt()).isNotNull();
    }

    @Test
    void admin_admin_still_logs_in() {
        String token = login("admin", "admin");
        assertThat(token).isNotBlank();
    }

    @Test
    void jwt_carries_role_claim() {
        String token = login("admin", "admin");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("ADMIN");
    }

    @Test
    void login_updates_last_login_at() {
        User before = userRepository.findByUsername("admin").orElseThrow();
        assertThat(before.getLastLoginAt()).isNull();

        login("admin", "admin");

        User after = userRepository.findByUsername("admin").orElseThrow();
        assertThat(after.getLastLoginAt()).isNotNull();
    }

    @Test
    void blocked_user_rejected_at_login() {
        User blocked = User.builder()
                .username("blocked-user")
                .passwordHash(passwordEncoder.encode("pass1234"))
                .role("USER")
                .status("BLOCKED")
                .accountType("REGULAR")
                .build();
        userRepository.save(blocked);

        var body = new java.util.HashMap<String, String>();
        body.put("username", "blocked-user");
        body.put("password", "pass1234");

        webTestClient.post()
                .uri("/api/v1/auth/login")
                .bodyValue(body)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void blocked_user_rejected_with_previously_valid_token() {
        User user = User.builder()
                .username("to-block")
                .passwordHash(passwordEncoder.encode("pass1234"))
                .role("USER")
                .status("ACTIVE")
                .accountType("REGULAR")
                .build();
        userRepository.save(user);

        String token = login("to-block", "pass1234");

        User reloaded = userRepository.findByUsername("to-block").orElseThrow();
        reloaded.setStatus("BLOCKED");
        userRepository.save(reloaded);

        webTestClient.get()
                .uri("/api/v1/health")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void expired_demo_user_rejected_with_valid_token() {
        User user = User.builder()
                .username("demo-expired-test")
                .passwordHash(passwordEncoder.encode("pass1234"))
                .role("USER")
                .status("ACTIVE")
                .accountType("DEMO")
                .expiresAt(java.time.Instant.now().plusSeconds(60))
                .build();
        userRepository.save(user);

        String token = login("demo-expired-test", "pass1234");

        User reloaded = userRepository.findByUsername("demo-expired-test").orElseThrow();
        reloaded.setExpiresAt(java.time.Instant.now().minusSeconds(60));
        userRepository.save(reloaded);

        webTestClient.get()
                .uri("/api/v1/health")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void me_endpoint_reports_role_and_account_type() {
        WebTestClient authed = authedAdminClient();

        authed.get()
                .uri("/api/v1/auth/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.username").isEqualTo("admin")
                .jsonPath("$.role").isEqualTo("ADMIN")
                .jsonPath("$.accountType").isEqualTo("REGULAR")
                .jsonPath("$.onboardingCompleted").isEqualTo(true);
    }
}
