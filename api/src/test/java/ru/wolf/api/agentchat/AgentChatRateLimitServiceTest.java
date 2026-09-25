package ru.wolf.api.agentchat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.wolf.api.importer.ImportBotDailyUsageRepository;
import ru.wolf.api.importer.ImportBotProperties;
import ru.wolf.api.user.User;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentChatRateLimitServiceTest {

    @Mock
    ImportBotDailyUsageRepository usageRepository;

    private final ImportBotProperties properties = new ImportBotProperties();
    private AgentChatRateLimitService service;

    @BeforeEach
    void setUp() {
        properties.setDailyLimitPerUser(2);
        service = new AgentChatRateLimitService(usageRepository, properties);
    }

    @Test
    void regular_user_does_not_consume_demo_budget() {
        User regular = User.builder().id(1L).accountType("REGULAR").build();

        assertThatCode(() -> service.consume(regular)).doesNotThrowAnyException();

        verifyNoInteractions(usageRepository);
    }

    @Test
    void demo_user_is_refused_after_atomic_counter_reaches_limit() {
        User demo = User.builder().id(2L).accountType("DEMO").build();
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        when(usageRepository.tryConsume(eq(2L), eq(today), eq(2)))
                .thenReturn(1, 1, 0);

        service.consume(demo);
        service.consume(demo);

        assertThatThrownBy(() -> service.consume(demo))
                .isInstanceOf(AgentChatRateLimitExceededException.class)
                .hasMessage("Дневной лимит запросов агента для демо-профиля исчерпан. Попробуйте завтра.");

        verify(usageRepository, org.mockito.Mockito.times(3))
                .tryConsume(eq(2L), eq(today), eq(2));
    }

    @Test
    void non_positive_limit_keeps_existing_unlimited_configuration() {
        properties.setDailyLimitPerUser(0);
        User demo = User.builder().id(2L).accountType("DEMO").build();

        assertThatCode(() -> service.consume(demo)).doesNotThrowAnyException();

        verifyNoInteractions(usageRepository);
    }
}
