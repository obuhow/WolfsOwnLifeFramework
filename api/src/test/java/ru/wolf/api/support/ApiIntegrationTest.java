package ru.wolf.api.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for WOLF HTTP API integration tests (primary seam).
 *
 * <p>Full Spring context + real PostgreSQL (Testcontainers). Prefer {@link WebTestClient}
 * assertions on external behaviour. Ticket 02 will add JWT helpers here (e.g. {@code
 * withBearer(token)}) so later tickets stay authenticated-ready without reinventing setup.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class ApiIntegrationTest {

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:14-alpine")
            .withDatabaseName("wolf")
            .withUsername("wolf")
            .withPassword("wolf")
            .withStartupTimeout(java.time.Duration.ofMinutes(3));

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @LocalServerPort
    int port;

    @Autowired
    protected WebTestClient webTestClient;

    /** Obtain a JWT for the given credentials and return an authenticated WebTestClient. */
    protected WebTestClient authedClient(String username, String password) {
        String token = login(username, password);
        return webTestClient.mutate()
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
    }

    /** Obtain a JWT for the default seed admin user. */
    protected WebTestClient authedAdminClient() {
        return authedClient("admin", "admin");
    }

    /** Login and return JWT token. */
    protected String login(String username, String password) {
        var loginRequest = new java.util.HashMap<String, String>();
        loginRequest.put("username", username);
        loginRequest.put("password", password);

        String responseBody = webTestClient.post()
                .uri("/api/v1/auth/login")
                .bodyValue(loginRequest)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseBody()
                .blockFirst();

        // Parse JSON to extract token
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(responseBody)
                    .get("token")
                    .asText();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to parse login response", e);
        }
    }

    /** Hook for ticket 02+: attach Authorization without changing call sites much. */
    @Deprecated(forRemoval = true, since = "0.2")
    protected WebTestClient.Builder authedClient() {
        return webTestClient.mutate();
    }
}
