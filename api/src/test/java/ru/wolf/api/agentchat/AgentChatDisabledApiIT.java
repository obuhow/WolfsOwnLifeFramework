package ru.wolf.api.agentchat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.agentchat.dto.ChatSessionResponse;
import ru.wolf.api.support.ApiIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@TestPropertySource(properties = "wolf.llm.enabled=false")
class AgentChatDisabledApiIT extends ApiIntegrationTest {

    @DynamicPropertySource
    static void postgresDriver(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    ChatSessionRepository sessionRepository;

    @Autowired
    ChatMessageRepository messageRepository;

    @BeforeEach
    void cleanup() {
        messageRepository.deleteAll();
        sessionRepository.deleteAll();
    }

    @Test
    void disabled_agent_returns_unavailable_without_writing_messages() {
        WebTestClient client = authedAdminClient();
        ChatSessionResponse session = client.post()
                .uri("/api/v1/agent-chat/sessions")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatSessionResponse.class)
                .returnResult().getResponseBody();

        client.post()
                .uri("/api/v1/agent-chat/sessions/{id}/chat", session.id())
                .bodyValue(java.util.Map.of("content", "Проверь статус"))
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.message")
                .isEqualTo("LLM-функции отключены: установите wolf.llm.enabled=true");

        assertThat(messageRepository.count()).isZero();
    }
}
