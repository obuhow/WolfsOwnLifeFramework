package ru.wolf.api.agentchat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.wolf.api.importer.ImportBotRateLimitService;
import ru.wolf.api.user.User;

@Service
@RequiredArgsConstructor
public class AgentChatRateLimitService {

    static final String LIMIT_MESSAGE =
            "Дневной лимит запросов агента для демо-профиля исчерпан. Попробуйте завтра.";
    private final ImportBotRateLimitService rateLimitService;

    /** Consume the shared import/agent budget only for DEMO accounts. */
    public void consume(User user) {
        if (!"DEMO".equals(user.getAccountType())) {
            return;
        }
        if (!rateLimitService.tryConsume(user.getId())) {
            throw new AgentChatRateLimitExceededException(LIMIT_MESSAGE);
        }
    }
}