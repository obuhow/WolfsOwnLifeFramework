/*
 * WOLF — Wolf's Own Life Framework
 * Copyright (C) 2025 Pavel Obukhov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.wolf.api.lifesphere.LifeSphereSeeder;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LifeSphereSeeder lifeSphereSeeder;

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
                    .role("ADMIN")
                    .status("ACTIVE")
                    .accountType("REGULAR")
                    .onboardingCompletedAt(java.time.Instant.now())
                    .build();
            userRepository.save(admin);
        }

        // Seed life spheres for admin user
        User admin = userRepository.findByUsername("admin").orElseThrow();
        lifeSphereSeeder.seed(admin);

        // Release 0.4 ticket 12: obuhov is created by migration V38__admin_obuhov.sql;
        // seed its life spheres the same way as any new user (idempotent, no-op if already seeded).
        userRepository.findByUsername("obuhov").ifPresent(lifeSphereSeeder::seed);
    }
}
