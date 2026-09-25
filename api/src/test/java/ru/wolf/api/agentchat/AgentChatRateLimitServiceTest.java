package ru.wolf.api.agentchat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.wolf.api.importer.ImportBotRateLimitService;
import ru.wolf.api.user.User;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentChatRateLimitServiceTest {

    @Mock
    ImportBotRateLimitService rateLimitService;

    private AgentChatRateLimitService service;

    @BeforeEach
    void setUp() {
        service = new AgentChatRateLimitService(rateLimitService);
    }

    @Test
    void regular_user_does_not_consume_demo_budget() {
        User regular = User.builder().id(1L).accountType("REGULAR").build();

        assertThatCode(() -> service.consume(regular)).doesNotThrowAnyException();

        verifyNoInteractions(rateLimitService);
    }

    @Test
    void demo_user_is_refused_after_atomic_counter_reaches_limit() {
        User demo = User.builder().id(2L).accountType("DEMO").build();
        when(rateLimitService.tryConsume(eq(2L))).thenReturn(true, true, false);

        service.consume(demo);
        service.consume(demo);

        assertThatThrownBy(() -> service.consume(demo))
                .isInstanceOf(AgentChatRateLimitExceededException.class)
                .hasMessage("Дневной лимит запросов агента для демо-профиля исчерпан. Попробуйте завтра.");

        verify(rateLimitService, org.mockito.Mockito.times(3)).tryConsume(eq(2L));
    }

    @Test
    void unlimited_configuration_is_delegated_to_shared_budget() {
        User demo = User.builder().id(2L).accountType("DEMO").build();
        when(rateLimitService.tryConsume(eq(2L))).thenReturn(true);

        assertThatCode(() -> service.consume(demo)).doesNotThrowAnyException();

        verify(rateLimitService).tryConsume(eq(2L));
    }
}
