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
package ru.wolf.api.invite;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.auth.JwtUtil;
import ru.wolf.api.lifesphere.LifeSphereSeeder;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InviteService {

    private final InviteCodeRepository inviteCodeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LifeSphereSeeder lifeSphereSeeder;

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    @Transactional
    public InviteCode createInvite(User createdBy, int maxUses, Instant expiresAt, String note) {
        String code = generateCode();
        while (inviteCodeRepository.findByCode(code).isPresent()) {
            code = generateCode();
        }
        InviteCode invite = InviteCode.builder()
                .code(code)
                .createdBy(createdBy)
                .maxUses(maxUses)
                .usedCount(0)
                .expiresAt(expiresAt)
                .revokedAt(null)
                .note(note)
                .build();
        return inviteCodeRepository.save(invite);
    }

    @Transactional
    public Optional<String> registerWithInvite(String code, String username, String rawPassword) {
        Optional<InviteCode> inviteOpt = inviteCodeRepository.findByCode(code);
        if (inviteOpt.isEmpty()) {
            return Optional.empty();
        }
        InviteCode invite = inviteOpt.get();
        if (invite.getRevokedAt() != null) {
            return Optional.empty();
        }
        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        if (invite.getUsedCount() >= invite.getMaxUses()) {
            return Optional.empty();
        }

        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Имя занято");
        }
        if (username.length() < 3 || username.length() > 100) {
            throw new IllegalArgumentException("Имя должно быть от 3 до 100 символов");
        }
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("Имя может содержать только латинские буквы, цифры и подчёркивание");
        }
        if (rawPassword.length() < 8) {
            throw new IllegalArgumentException("Пароль должен быть не менее 8 символов");
        }

        User newUser = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role("USER")
                .status("ACTIVE")
                .accountType("REGULAR")
                .onboardingCompletedAt(null)
                .build();
        userRepository.save(newUser);

        lifeSphereSeeder.seed(newUser);

        int updated = inviteCodeRepository.incrementUsedCountIfValid(code);
        if (updated == 0) {
            throw new IllegalStateException("Invite code no longer valid");
        }

        String token = jwtUtil.generateToken(newUser.getUsername(), newUser.getId(), newUser.getRole());
        return Optional.of(token);
    }
}