package ru.wolf.api.demo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserPurgeService;
import ru.wolf.api.user.UserRepository;

import java.time.Instant;
import java.util.List;

/**
 * Hourly garbage collector for expired demo accounts.
 * Only runs when demo.gc-enabled is true (default: false).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DemoGcService {

    private final UserRepository userRepository;
    private final UserPurgeService userPurgeService;

    @Value("${wolf.demo.gc-enabled:false}")
    private boolean gcEnabled;

    @Value("${wolf.demo.ttl-hours:24}")
    private int ttlHours;

    @Scheduled(cron = "0 0 * * * *") // Every hour at minute 0
    @Transactional
    public void collectExpiredDemoAccounts() {
        if (!gcEnabled) {
            log.debug("Demo GC is disabled, skipping");
            return;
        }

        Instant now = Instant.now();
        List<User> expiredDemos = userRepository.findExpiredDemoAccounts(now);
        
        if (expiredDemos.isEmpty()) {
            log.debug("No expired demo accounts to clean up");
            return;
        }

        int deletedCount = 0;
        for (User demoUser : expiredDemos) {
            try {
                userPurgeService.purgeUser(demoUser);
                userRepository.delete(demoUser);
                deletedCount++;
                log.info("Deleted expired demo account: {}", demoUser.getUsername());
            } catch (Exception e) {
                log.error("Failed to delete demo account {}: {}", demoUser.getUsername(), e.getMessage());
            }
        }

        log.info("Demo GC completed: deleted {} expired demo accounts", deletedCount);
    }
}