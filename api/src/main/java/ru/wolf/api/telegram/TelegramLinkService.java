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
 * along with this program. if not, see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.telegram;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.telegram.dto.TelegramLinkStatus;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

/**
 * Account-link lifecycle for the Telegram import channel (ticket 03, points 1–2).
 *
 * <p>The link is a separate table, not an extension of {@link User}. A one-time
 * token (10-minute TTL) is generated in Settings, the user sends it to the bot
 * as {@code /start <token>}, and the bot binds the Telegram {@code chat_id} to
 * the WOLF {@code userId}. The bot never stores identity beyond this binding.
 */
@Service
@RequiredArgsConstructor
public class TelegramLinkService {

    /** Token lifetime for the account-link deep link. */
    static final int TOKEN_TTL_MINUTES = 10;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TelegramLinkRepository linkRepository;
    private final TelegramLinkTokenRepository tokenRepository;
    private final TelegramPendingImportRepository pendingRepository;
    private final UserRepository userRepository;
    private final TelegramProperties properties;

    /** Issue a fresh link token for the user and the deep link to open in Telegram. */
    @Transactional
    public TelegramLinkStatus issueToken(String username) {
        User user = currentUser(username);
        String token = generateToken();
        tokenRepository.save(TelegramLinkToken.builder()
                .token(token)
                .userId(user.getId())
                .expiresAt(Instant.now().plus(TOKEN_TTL_MINUTES, ChronoUnit.MINUTES))
                .build());
        return status(user.getId(), token);
    }

    /** Resolve and consume a {@code /start <token>} deep link → bind chat to user. */
    @Transactional
    public boolean linkAccount(String token, String chatId) {
        if (token == null || token.isBlank() || chatId == null || chatId.isBlank()) {
            return false;
        }
        TelegramLinkToken linkToken = tokenRepository.findByToken(token).orElse(null);
        if (linkToken == null || linkToken.getUsedAt() != null
                || linkToken.getExpiresAt().isBefore(Instant.now())) {
            return false;
        }
        Long userId = linkToken.getUserId();
        linkRepository.findByChatId(chatId).ifPresent(linkRepository::delete);
        linkRepository.save(TelegramLink.builder()
                .chatId(chatId)
                .userId(userId)
                .build());
        linkToken.setUsedAt(Instant.now());
        tokenRepository.save(linkToken);
        return true;
    }

    /** Current link state for Settings (re-issues a token when not yet linked). */
    @Transactional(readOnly = true)
    public TelegramLinkStatus getStatus(String username) {
        User user = currentUser(username);
        Optional<TelegramLink> link = linkRepository.findByUserId(user.getId());
        if (link.isPresent()) {
            return new TelegramLinkStatus(true, link.get().getChatId(), null, null,
                    properties.getBotUsername());
        }
        // Not linked yet — surface a fresh token so the user can complete linking.
        return issueToken(username);
    }

    /** Remove the user's Telegram binding and any pending confirmation. */
    @Transactional
    public void unlink(String username) {
        User user = currentUser(username);
        linkRepository.findByUserId(user.getId()).ifPresent(linkRepository::delete);
        pendingRepository.findByChatId(resolveChatId(user.getId()).orElse(""))
                .ifPresent(pendingRepository::delete);
    }

    /** Resolve a WOLF userId from a Telegram chat id. Empty → no account linked. */
    @Transactional(readOnly = true)
    public Optional<Long> resolveUserId(String chatId) {
        return linkRepository.findByChatId(chatId).map(TelegramLink::getUserId);
    }

    Optional<String> resolveChatId(Long userId) {
        return linkRepository.findByUserId(userId).map(TelegramLink::getChatId);
    }

    private TelegramLinkStatus status(Long userId, String token) {
        String bot = properties.getBotUsername();
        String url = bot == null || bot.isBlank()
                ? ""
                : "https://t.me/" + bot + "?start=" + token;
        return new TelegramLinkStatus(false, null, token, url, bot);
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
