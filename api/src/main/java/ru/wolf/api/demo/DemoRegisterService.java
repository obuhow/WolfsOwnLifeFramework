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
package ru.wolf.api.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.auth.JwtUtil;
import ru.wolf.api.demo.dto.DemoRegisterResponse;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Public demo registration (release 1.0, ticket 07). Creates an isolated {@code accountType=DEMO}
 * user pre-filled from one of the three known profiles and returns a fresh JWT so the SPA can log
 * the guest straight in. Login is {@code GoodEnoughDemoUser-<A-Z><N>} (A1..Z1, A2..Z2, …) and the
 * password equals the login by design; demo accounts are deleted by an admin (no auto-expiry).
 */
@Service
@RequiredArgsConstructor
public class DemoRegisterService {

    private static final String LOGIN_PREFIX = "GoodEnoughDemoUser-";
    private static final List<String> KNOWN_SLUGS =
            List.of("worker-class", "wise-freelancer", "free-artist");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DemoFixtureGenerator demoFixtureGenerator;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;

    @Transactional
    public DemoRegisterResponse register(String rawSlug) {
        String slug = normalizeSlug(rawSlug);

        String username = nextFreeLogin();
        User user = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(username)) // пароль = логин (тикет 07)
                .role("USER")
                .status("ACTIVE")
                .accountType("DEMO")
                // онбординг для демо считается пройденным — не запираем гостя в мастере
                .onboardingCompletedAt(Instant.now())
                .build();
        userRepository.save(user);

        String displayName = applyProfile(user, slug);

        String token = jwtUtil.generateToken(user.getUsername(), user.getId(), user.getRole());
        return new DemoRegisterResponse(username, username, token, displayName);
    }

    /**
     * First free login slot in order A1,B1,…,Z1,A2,…,Z2,… — a demo account that was later deleted
     * frees its slot, so gaps are reused rather than skipped.
     */
    private String nextFreeLogin() {
        Set<String> taken = userRepository.findAll().stream()
                .filter(user -> "DEMO".equals(user.getAccountType()))
                .map(User::getUsername)
                .filter(name -> name != null && name.startsWith(LOGIN_PREFIX))
                .collect(Collectors.toSet());
        // Also guard against any REGULAR user squatting the pattern (belt and suspenders).
        for (int number = 1; number <= 10_000; number++) {
            for (char letter = 'A'; letter <= 'Z'; letter++) {
                String candidate = LOGIN_PREFIX + letter + number;
                if (!taken.contains(candidate) && userRepository.findByUsername(candidate).isEmpty()) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("Демо-слоты исчерпаны");
    }

    /** Load the profile JSON from classpath and populate the fresh user; returns displayName. */
    private String applyProfile(User user, String slug) {
        String resourcePath = "profiles/" + slug + ".json";
        try (InputStream is = new ClassPathResource(resourcePath).getInputStream()) {
            DemoProfile profile = objectMapper.readValue(is, DemoProfile.class);
            demoFixtureGenerator.populate(user, profile);
            user.setTimezone(profile.timezone() != null ? profile.timezone() : "Europe/Moscow");
            if (profile.weeklyHoursNorm() != null) {
                user.setAvailableWeeklyHours(profile.weeklyHoursNorm());
            }
            userRepository.save(user);
            return profile.displayName();
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось загрузить профиль: " + slug, e);
        }
    }

    private String normalizeSlug(String rawSlug) {
        if (rawSlug == null) {
            throw new IllegalArgumentException("Профиль не указан");
        }
        String slug = rawSlug.trim().toLowerCase(Locale.ROOT);
        if (!KNOWN_SLUGS.contains(slug)) {
            throw new IllegalArgumentException("Неизвестный профиль: " + slug);
        }
        return slug;
    }
}
