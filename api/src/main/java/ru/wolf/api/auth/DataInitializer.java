package ru.wolf.api.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.wolf.api.lifesphere.LifeSphere;
import ru.wolf.api.lifesphere.LifeSphereRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LifeSphereRepository lifeSphereRepository;

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

        // Seed life spheres for admin user
        User admin = userRepository.findByUsername("admin").orElseThrow();
        seedLifeSpheres(admin);
    }

    private void seedLifeSpheres(User user) {
        List<LifeSphere> existing = lifeSphereRepository.findByUserOrderBySortOrderAscNameAsc(user);
        if (!existing.isEmpty()) {
            return; // already seeded
        }

        String[][] spheres = {
                {"Здоровье", "0", "#EF4444"},
                {"Навык QA Java", "1", "#3B82F6"},
                {"Навык музыканта", "2", "#8B5CF6"},
                {"Общение на расстоянии", "3", "#06B6D4"},
                {"Мотивация к делам", "4", "#F59E0B"},
                {"Ресурсы/деньги", "5", "#10B981"},
                {"Ресурсы/время", "6", "#84CC16"},
                {"Открытие новых ходов", "7", "#EC4899"},
                {"Независимость", "8", "#6366F1"}
        };

        for (String[] s : spheres) {
            LifeSphere sphere = LifeSphere.builder()
                    .user(user)
                    .name(s[0])
                    .sortOrder(Integer.parseInt(s[1]))
                    .color(s[2])
                    .build();
            lifeSphereRepository.save(sphere);
        }
    }
}
