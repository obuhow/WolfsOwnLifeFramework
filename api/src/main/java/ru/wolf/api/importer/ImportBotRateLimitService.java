package ru.wolf.api.importer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/** Atomic shared budget for every LLM import channel and the demo agent chat. */
@Service
@RequiredArgsConstructor
public class ImportBotRateLimitService {

    private static final ZoneId RATE_LIMIT_ZONE = ZoneId.of("UTC");

    private final ImportBotDailyUsageRepository usageRepository;
    private final ImportBotProperties properties;

    /**
     * Consume one request from the shared daily budget. A non-positive configured
     * limit retains the existing unlimited-mode behaviour.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryConsume(Long userId) {
        int limit = properties.getDailyLimitPerUser();
        if (limit <= 0) {
            return true;
        }
        return usageRepository.tryConsume(
                userId, LocalDate.now(RATE_LIMIT_ZONE), limit) == 1;
    }
}