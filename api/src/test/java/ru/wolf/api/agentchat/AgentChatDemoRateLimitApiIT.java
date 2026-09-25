package ru.wolf.api.agentchat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.wolf.api.importer.ImportBotDailyUsageRepository;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;
import ru.wolf.api.agentchat.dto.ChatSessionResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@TestPropertySource(properties = {
        "wolf.llm.enabled=true",
        "wolf.llm.model=test-planner-model",
        "wolf.import-bot.daily-limit-per-user=2"
})
class AgentChatDemoRateLimitApiIT extends ApiIntegrationTest {

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
    ImportBotDailyUsageRepository usageRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    FakeAgentChatAdapter fakeAgent;

    @BeforeEach
    void cleanup() {
        messageRepository.deleteAll();
        sessionRepository.deleteAll();
        usageRepository.deleteAll();
        fakeAgent.reset();
    }

    @Test
    void demo_user_is_limited_and_provider_is_not_called_after_limit() {
        WebTestClient client = demoClient();
        ChatSessionResponse session = createSession(client);
        fakeAgent.setResponse("Демо-ответ");

        postChat(client, session.id(), "первый").expectStatus().isOk();
        postChat(client, session.id(), "второй").expectStatus().isOk();
        postChat(client, session.id(), "третий")
                .expectStatus().isEqualTo(429)
                .expectBody().jsonPath("$.message")
                .isEqualTo(AgentChatRateLimitService.LIMIT_MESSAGE);

        User demo = userRepository.findByUsername(currentDemoUsername).orElseThrow();
        assertThat(usageRepository.findByUserIdAndUsageDate(
                demo.getId(), LocalDate.now(ZoneId.of("UTC"))))
                .get().extracting(usage -> usage.getRequestCount()).isEqualTo(2);
        assertThat(fakeAgent.lastRequest().messages())
                .extracting(AgentChatPort.Message::content)
                .doesNotContain("третий");
    }

    @Test
    void regular_user_does_not_inherit_demo_limit() {
        WebTestClient client = authedAdminClient();
        ChatSessionResponse session = createSession(client);
        fakeAgent.setResponse("Ответ обычному пользователю");

        postChat(client, session.id(), "раз").expectStatus().isOk();
        postChat(client, session.id(), "два").expectStatus().isOk();
        postChat(client, session.id(), "три").expectStatus().isOk();

        assertThat(usageRepository.count()).isZero();
    }

    @Test
    void concurrent_demo_requests_consume_no_more_than_the_limit() {
        WebTestClient client = demoClient();
        ChatSessionResponse session = createSession(client);
        fakeAgent.setResponse("Параллельный ответ");

        List<Integer> statuses = Flux.range(0, 8)
                .flatMap(index -> Mono.fromCallable(() -> postChat(client, session.id(), "запрос-" + index)
                                .returnResult(Void.class)
                                .getStatus()
                                .value())
                        .subscribeOn(Schedulers.boundedElastic()), 8)
                .collectList()
                .block();

        assertThat(statuses).containsExactlyInAnyOrder(200, 200, 429, 429, 429, 429, 429, 429);
    }

    private String currentDemoUsername;

    private WebTestClient demoClient() {
        currentDemoUsername = "demo-rate-limit-" + UUID.randomUUID();
        userRepository.save(User.builder()
                .username(currentDemoUsername)
                .passwordHash(passwordEncoder.encode("demo-pass"))
                .role("USER")
                .status("ACTIVE")
                .accountType("DEMO")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build());
        return authedClient(currentDemoUsername, "demo-pass");
    }

    private ChatSessionResponse createSession(WebTestClient client) {
        return client.post()
                .uri("/api/v1/agent-chat/sessions")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatSessionResponse.class)
                .returnResult().getResponseBody();
    }

    private WebTestClient.ResponseSpec postChat(WebTestClient client, Long sessionId, String content) {
        return client.post()
                .uri("/api/v1/agent-chat/sessions/{id}/chat", sessionId)
                .bodyValue(java.util.Map.of("content", content))
                .exchange();
    }
}
