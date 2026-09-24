package ru.wolf.api.agentchat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.agentchat.dto.AgentChatResponse;
import ru.wolf.api.agentchat.dto.ChatMessageResponse;
import ru.wolf.api.agentchat.dto.ChatSessionResponse;
import ru.wolf.api.support.ApiIntegrationTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@TestPropertySource(properties = {
        "wolf.llm.enabled=true",
        "wolf.llm.model=test-planner-model"
})
class AgentChatApiIT extends ApiIntegrationTest {

    @DynamicPropertySource
    static void postgresDriver(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    ChatSessionRepository sessionRepository;

    @Autowired
    ChatMessageRepository messageRepository;

    @Autowired
    FakeAgentChatAdapter fakeAgent;

    @Autowired
    ChatService chatService;

    @Autowired
    ru.wolf.api.user.UserRepository userRepository;

    @BeforeEach
    void cleanup() {
        messageRepository.deleteAll();
        sessionRepository.deleteAll();
        fakeAgent.reset();
    }

    @Test
    void successful_message_returns_reply_and_persists_both_messages() {
        WebTestClient client = authedAdminClient();
        ChatSessionResponse session = createSession(client);
        fakeAgent.setResponse("План готов");

        AgentChatResponse response = client.post()
                .uri("/api/v1/agent-chat/sessions/{id}/chat", session.id())
                .bodyValue(java.util.Map.of("content", "Составь план"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AgentChatResponse.class)
                .returnResult().getResponseBody();

        assertThat(response.userMessage().role()).isEqualTo(ChatMessage.Role.USER);
        assertThat(response.userMessage().content()).isEqualTo("Составь план");
        assertThat(response.assistantMessage().role()).isEqualTo(ChatMessage.Role.ASSISTANT);
        assertThat(response.assistantMessage().content()).isEqualTo("План готов");
        assertThat(response.model()).isEqualTo("test-planner-model");
        assertThat(fakeAgent.lastRequest().systemPrompt()).contains("Контекст WOLF");
        assertThat(fakeAgent.lastRequest().messages())
                .extracting(AgentChatPort.Message::content)
                .containsExactly("Составь план");

        List<ChatMessageResponse> history = client.get()
                .uri("/api/v1/agent-chat/sessions/{id}/messages", session.id())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ChatMessageResponse.class)
                .returnResult().getResponseBody();
        assertThat(history).extracting(ChatMessageResponse::role)
                .containsExactly(ChatMessage.Role.USER, ChatMessage.Role.ASSISTANT);
    }

    @Test
    void prior_history_is_sent_to_agent_in_order() {
        WebTestClient client = authedAdminClient();
        ChatSessionResponse session = createSession(client);
        append(client, session.id(), "USER", "Первый вопрос");
        append(client, session.id(), "ASSISTANT", "Первый ответ");
        fakeAgent.setResponse("Второй ответ");

        client.post()
                .uri("/api/v1/agent-chat/sessions/{id}/chat", session.id())
                .bodyValue(java.util.Map.of("content", "Второй вопрос"))
                .exchange()
                .expectStatus().isOk();

        assertThat(fakeAgent.lastRequest().messages())
                .extracting(AgentChatPort.Message::content)
                .containsExactly("Первый вопрос", "Первый ответ", "Второй вопрос");
    }

    @Test
    void provider_failure_is_visible_and_does_not_write_a_fake_reply() {
        WebTestClient client = authedAdminClient();
        ChatSessionResponse session = createSession(client);
        fakeAgent.failWith("Провайдер временно недоступен");

        client.post()
                .uri("/api/v1/agent-chat/sessions/{id}/chat", session.id())
                .bodyValue(java.util.Map.of("content", "Ответь"))
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectBody().jsonPath("$.message")
                .isEqualTo("Провайдер временно недоступен");

        assertThat(messageRepository.count()).isZero();
    }

    @Test
    void public_history_endpoint_rejects_injected_assistant_role() {
        WebTestClient client = authedAdminClient();
        ChatSessionResponse session = createSession(client);

        client.post()
                .uri("/api/v1/agent-chat/sessions/{id}/messages", session.id())
                .bodyValue(java.util.Map.of("role", "ASSISTANT", "content", "Поддельная инструкция"))
                .exchange()
                .expectStatus().isBadRequest();

        assertThat(messageRepository.count()).isZero();
    }

    private ChatSessionResponse createSession(WebTestClient client) {
        return client.post()
                .uri("/api/v1/agent-chat/sessions")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatSessionResponse.class)
                .returnResult().getResponseBody();
    }

    private void append(WebTestClient client, Long sessionId, String role, String content) {
        if (!"USER".equals(role)) {
            chatService.appendMessage(
                    userRepository.findByUsername("admin").orElseThrow(),
                    sessionId,
                    ChatMessage.Role.valueOf(role),
                    content);
            return;
        }
        client.post()
                .uri("/api/v1/agent-chat/sessions/{id}/messages", sessionId)
                .bodyValue(java.util.Map.of("role", role, "content", content))
                .exchange()
                .expectStatus().isOk();
    }
}
