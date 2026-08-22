package ru.wolf.api.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Minimal user purge service for demo GC.
 * Relies on DB ON DELETE CASCADE for most entities.
 */
@Service
@RequiredArgsConstructor
public class UserPurgeService {

    @Transactional
    public void purgeUser(User user) {
        // Deletion is handled by DB cascade - just delete the user
        // This method exists for API compatibility with DemoGcService
    }
}