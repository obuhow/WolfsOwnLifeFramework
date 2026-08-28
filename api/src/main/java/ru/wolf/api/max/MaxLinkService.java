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
 * GNU Affero General License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. if not, see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.max;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.max.dto.MaxLinkStatus;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

/**
 * Account-link lifecycle for the Max import channel (release 0.7, ticket 04,
 * points 1–2). Mirrors {@link ru.wolf.api.telegram.TelegramLinkService}: the link
 * is a separate table, a one-time token (10-minute TTL) is generated in Settings,
 * and the bot binds the Max chat id to the WOLF userId when it receives the
 * {@code bot_started} event carrying that token as the deep-link payload.
 *
 * <p>Max deep link format is {@code max.ru/<bot>?start=<payload>} — the payload
 * (up to 128 characters) is delivered verbatim via {@code bot_started.payload},
 * which is exactly the one-time token mechanism used here.
 */
@Service
@RequiredArgsConstructor
public class MaxLinkService {

    /** Token lifetime for the account-link deep link. */
    static final int TOKEN_TTL_MINUTES = 10;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final MaxLinkRepository linkRepository;
    private final MaxLinkTokenRepository tokenRepository;
    private final MaxPendingImportRepository pendingRepository;
    private final UserRepository userRepository;
    private final MaxProperties properties;

    /** Issue a fresh link token for the user and the deep link to open in Max. */
    @Transactional
    public MaxLinkStatus issueToken(String username) {
        User user = currentUser(username);
        String token = generateToken();
        tokenRepository.save(MaxLinkToken.builder()
                .token(token)
                .userId(user.getId())
                .expiresAt(Instant.now().plus(TOKEN_TTL_MINUTES, ChronoUnit.MINUTES))
                .build());
        return status(user.getId(), token);
    }

    /** Resolve and consume a {@code bot_started} deep link → bind chat to user. */
    @Transactional
    public boolean linkAccount(String token, String chatId) {
        if (token == null || token.isBlank() || chatId == null || chatId.isBlank()) {
            return false;
        }
        MaxLinkToken linkToken = tokenRepository.findByToken(token).orElse(null);
        if (linkToken == null || linkToken.getUsedAt() != null
                || linkToken.getExpiresAt().isBefore(Instant.now())) {
            return false;
        }
        Long userId = linkToken.getUserId();
        linkRepository.findByChatId(chatId).ifPresent(linkRepository::delete);
        linkRepository.save(MaxLink.builder()
                .chatId(chatId)
                .userId(userId)
                .build());
        linkToken.setUsedAt(Instant.now());
        tokenRepository.save(linkToken);
        return true;
    }

    /** Current link state for Settings (re-issues a token when not yet linked). */
    @Transactional(readOnly = true)
    public MaxLinkStatus getStatus(String username) {
        User user = currentUser(username);
        Optional<MaxLink> link = linkRepository.findByUserId(user.getId());
        if (link.isPresent()) {
            return new MaxLinkStatus(true, link.get().getChatId(), null, null,
                    properties.getBotUsername());
        }
        return issueToken(username);
    }

    /** Remove the user's Max binding and any pending confirmation. */
    @Transactional
    public void unlink(String username) {
        User user = currentUser(username);
        linkRepository.findByUserId(user.getId()).ifPresent(linkRepository::delete);
        pendingRepository.findByChatId(resolveChatId(user.getId()).orElse(""))
                .ifPresent(pendingRepository::delete);
    }

    /** Resolve a WOLF userId from a Max chat id. Empty → no account linked. */
    @Transactional(readOnly = true)
    public Optional<Long> resolveUserId(String chatId) {
        return linkRepository.findByChatId(chatId).map(MaxLink::getUserId);
    }

    Optional<String> resolveChatId(Long userId) {
        return linkRepository.findByUserId(userId).map(MaxLink::getChatId);
    }

    private MaxLinkStatus status(Long userId, String token) {
        String bot = properties.getBotUsername();
        String url = bot == null || bot.isBlank()
                ? ""
                : "https://max.ru/" + bot + "?start=" + token;
        return new MaxLinkStatus(false, null, token, url, bot);
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found: " + username));
    }

    private static String generateToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
