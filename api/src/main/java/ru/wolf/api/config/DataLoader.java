package ru.wolf.api.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

/**
 * Ensures a default single-user "admin" account exists on startup so the
 * data-sync import endpoints (which resolve the default user) always have a
 * user to attach imported entities to. Idempotent: does nothing if admin exists.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        String username = "admin";
        if (userRepository.findByUsername(username).isEmpty()) {
            User admin = User.builder()
                    .username(username)
                    .passwordHash(passwordEncoder.encode("admin"))
                    .build();
            userRepository.save(admin);
            log.info("Created default admin user");
        } else {
            log.info("Admin user already exists, skipping creation");
        }
    }
}
