package ru.wolf.api.agentchat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.agentchat.dto.ChatMessageResponse;
import ru.wolf.api.agentchat.dto.ChatSessionResponse;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatApiIT extends ApiIntegrationTest {

    @Autowired
    ChatSessionRepository sessionRepository;

    @Autowired
    ChatMessageRepository messageRepository;

    @Autowired
    ChatService chatService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanup() {
        messageRepository.deleteAll();
        sessionRepository.deleteAll();
    }

    @Test
    void messages_survive_reopening_and_keep_order_across_pages() {
        WebTestClient client = authedAdminClient();
        ChatSessionResponse session = client.post()
                .uri("/api/v1/agent-chat/sessions")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatSessionResponse.class)
                .returnResult().getResponseBody();

        append(client, session.id(), "USER", "Составь план");
        append(client, session.id(), "ASSISTANT", "Начну с расписания");
        append(client, session.id(), "USER", "Учти проект WOLF");

        List<ChatMessageResponse> firstPage = messages(client, session.id(), 0, 2);
        List<ChatMessageResponse> secondPage = messages(authedAdminClient(), session.id(), 1, 2);

        assertThat(firstPage).extracting(ChatMessageResponse::content)
                .containsExactly("Составь план", "Начну с расписания");
        assertThat(secondPage).extracting(ChatMessageResponse::content)
                .containsExactly("Учти проект WOLF");
        assertThat(messages(client, session.id(), 0, 100))
                .extracting(ChatMessageResponse::id)
                .containsExactly(firstPage.get(0).id(), firstPage.get(1).id(), secondPage.get(0).id());

        List<ChatSessionResponse> sessions = authedAdminClient().get()
                .uri("/api/v1/agent-chat/sessions")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ChatSessionResponse.class)
                .returnResult().getResponseBody();
        assertThat(sessions).extracting(ChatSessionResponse::id).contains(session.id());
    }

    @Test
    void session_is_not_visible_to_another_user() {
        WebTestClient admin = authedAdminClient();
        ChatSessionResponse session = admin.post()
                .uri("/api/v1/agent-chat/sessions")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatSessionResponse.class)
                .returnResult().getResponseBody();

        String username = "chat-other-" + UUID.randomUUID();
        userRepository.save(User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode("password123"))
                .build());

        WebTestClient other = authedClient(username, "password123");
        other.get()
                .uri("/api/v1/agent-chat/sessions/{id}", session.id())
                .exchange()
                .expectStatus().isNotFound();
        other.get()
                .uri("/api/v1/agent-chat/sessions/{id}/messages", session.id())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void invalid_history_page_is_rejected() {
        WebTestClient client = authedAdminClient();
        ChatSessionResponse session = client.post()
                .uri("/api/v1/agent-chat/sessions")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatSessionResponse.class)
                .returnResult().getResponseBody();

        client.get()
                .uri(uri -> uri.path("/api/v1/agent-chat/sessions/{id}/messages")
                        .queryParam("limit", 201)
                        .build(session.id()))
                .exchange()
                .expectStatus().isBadRequest();
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
                .bodyValue(new java.util.HashMap<String, Object>() {{
                    put("role", role);
                    put("content", content);
                }})
                .exchange()
                .expectStatus().isOk();
    }

    private List<ChatMessageResponse> messages(
            WebTestClient client, Long sessionId, int page, int limit) {
        return client.get()
                .uri(uri -> uri.path("/api/v1/agent-chat/sessions/{id}/messages")
                        .queryParam("page", page)
                        .queryParam("limit", limit)
                        .build(sessionId))
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ChatMessageResponse.class)
                .returnResult().getResponseBody();
    }
}
