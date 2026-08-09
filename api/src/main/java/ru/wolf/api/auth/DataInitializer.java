package ru.wolf.api.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (!userRepository.existsByUsername("admin")) {
            User admin = User.builder()
                    .username("admin")
                    .passwordHash(passwordEncoder.encode("admin"))
                    .timezone("Europe/Moscow")
                    .nightStart(java.time.LocalTime.of(23, 0))
                    .nightEnd(java.time.LocalTime.of(7, 0))
                    .hourAccountingMode("PRIMARY_ONLY")
                    .build();
            userRepository.save(admin);
        }
    }
}