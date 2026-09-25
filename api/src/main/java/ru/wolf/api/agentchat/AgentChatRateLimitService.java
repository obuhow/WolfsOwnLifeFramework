package ru.wolf.api.agentchat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.wolf.api.importer.ImportBotDailyUsageRepository;
import ru.wolf.api.importer.ImportBotProperties;
import ru.wolf.api.user.User;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class AgentChatRateLimitService {

    static final String LIMIT_MESSAGE =
            "Дневной лимит запросов агента для демо-профиля исчерпан. Попробуйте завтра.";
    private static final ZoneId RATE_LIMIT_ZONE = ZoneId.of("UTC");

    private final ImportBotDailyUsageRepository usageRepository;
    private final ImportBotProperties properties;

    /** Consume the shared import/agent budget only for DEMO accounts. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consume(User user) {
        if (!"DEMO".equals(user.getAccountType())) {
            return;
        }
        int limit = properties.getDailyLimitPerUser();
        if (limit <= 0) {
            return;
        }

        int consumed = usageRepository.tryConsume(
                user.getId(), LocalDate.now(RATE_LIMIT_ZONE), limit);
        if (consumed != 1) {
            throw new AgentChatRateLimitExceededException(LIMIT_MESSAGE);
        }
    }
}