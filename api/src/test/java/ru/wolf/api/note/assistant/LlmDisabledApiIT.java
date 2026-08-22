package ru.wolf.api.note.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.support.ApiIntegrationTest;

@ActiveProfiles("test")
@TestPropertySource(properties = "wolf.llm.enabled=false")
class LlmDisabledApiIT extends ApiIntegrationTest {

    @Test
    void llm_endpoints_return_service_unavailable_when_disabled() {
        WebTestClient client = authedAdminClient();
        client.get().uri("/api/v1/projects/{id}/resume", 1L)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.message").isEqualTo(
                        "LLM-функции отключены: установите wolf.llm.enabled=true");
    }
}
