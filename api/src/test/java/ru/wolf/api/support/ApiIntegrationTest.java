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
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("wolf")
            .withUsername("wolf")
            .withPassword("wolf");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Scaffold has no Flyway schema yet; allow empty DB to boot.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @LocalServerPort
    int port;

    @Autowired
    protected WebTestClient webTestClient;

    /** Hook for ticket 02+: attach Authorization without changing call sites much. */
    protected WebTestClient.Builder authedClient() {
        return webTestClient.mutate();
    }
}
