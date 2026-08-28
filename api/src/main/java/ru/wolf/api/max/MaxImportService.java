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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.importer.ImportBotDailyUsage;
import ru.wolf.api.importer.ImportBotDailyUsageRepository;
import ru.wolf.api.importer.ImportBotProperties;
import ru.wolf.api.importer.ImportConfirmService;
import ru.wolf.api.importer.ImportParserService;
import ru.wolf.api.importer.dto.ConfirmCandidate;
import ru.wolf.api.importer.dto.ConfirmImportRequest;
import ru.wolf.api.importer.dto.EntityKind;
import ru.wolf.api.importer.dto.ParseResult;
import ru.wolf.api.importer.dto.ParsedCandidate;
import ru.wolf.api.importer.dto.ParsedField;
import ru.wolf.api.max.dto.MaxCallbackQuery;
import ru.wolf.api.max.dto.MaxMessage;
import ru.wolf.api.max.dto.MaxUpdate;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

/**
 * Max import channel handler (release 0.7, ticket 04). Mirror of
 * {@link ru.wolf.api.telegram.TelegramImportService}: translates an inbound Max
 * update into WOLF entities through the shared {@link ImportParserService}. The
 * bot path carries no {@code SecurityContext} and never assumes the sender's
 * identity — the WOLF {@code userId} is resolved solely from the {@code max_link}
 * table by {@code chat_id}.
 *
 * <p>Per-update flow:
 * <ul>
 *   <li>{@code bot_started} with a {@code ?start=<token>} payload → bind account
 *       (delegated to {@link MaxLinkService});</li>
 *   <li>free text ({@code message_created}) → parse → a preview card with
 *       Принять/Отклонить inline buttons, or a single clarifying question when
 *       unparsed. Field editing is NOT offered in Max (only in the chat-panel
 *       channel) — reject with "Поправь в WOLF" + web link;</li>
 *   <li>inline tap ({@code message_callback}) → confirm (create entities via
 *       {@link ImportConfirmService}) or reject (discard the pending row).</li>
 * </ul>
 *
 * <p>The daily rate limit is the SHARED {@code import_bot_daily_usage} counter
 * (ticket 04, point 5): requests from Telegram and Max by the same user spend the
 * same per-user budget.
 *
 * <p>The bot never initiates a conversation: every outbound message here is a
 * direct reply to an inbound update.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MaxImportService {

    private static final String ACCEPT_PREFIX = "accept:";
    private static final String REJECT_PREFIX = "reject:";
    private static final String LIMIT_MESSAGE =
            "На сегодня лимит импорта исчерпан — попробуйте завтра или сохраните текст как заметку.";
    private static final String UNLINKED_MESSAGE =
            "Этот аккаунт ещё не привязан. Откройте «Импорт из Max» в Настройках WOLF и нажмите «Привязать Max».";

    private final MaxLinkService linkService;
    private final ImportParserService parserService;
    private final ImportConfirmService confirmService;
    private final MaxPendingImportRepository pendingRepository;
    private final ImportBotDailyUsageRepository usageRepository;
    private final MaxPort maxPort;
    private final UserRepository userRepository;
    private final ImportBotProperties importBotProperties;
    private final ObjectMapper objectMapper;

    /** Dispatch an inbound Max update: link, message, or callback. */
    @Transactional
    public void handleUpdate(MaxUpdate update) {
        if (update == null) {
            return;
        }
        if (update.isBotStarted()) {
            handleBotStarted(update);
            return;
        }
        if (update.isMessageCreated() && update.message() != null) {
            handleMessage(update.message());
            return;
        }
        if (update.isMessageCallback() && update.callback() != null) {
            handleCallback(update.callback());
        }
    }

    private void handleBotStarted(MaxUpdate update) {
        String chatId = update.chatId();
        if (chatId == null || chatId.isBlank()) {
            return;
        }
        String token = update.payload() == null ? "" : update.payload();
        boolean ok = linkService.linkAccount(token, chatId);
        if (ok) {
            maxPort.sendMessage(chatId, "Max привязан к вашему аккаунту WOLF. "
                    + "Присылайте заметки — я разберу их на Дела и Проекты.");
        } else {
            maxPort.sendMessage(chatId, UNLINKED_MESSAGE);
        }
    }

    /** Handle an inbound text message (parse → card, or clarify, or link hint). */
    @Transactional
    public void handleMessage(MaxMessage message) {
        String chatId = message.chatId();
        String messageId = message.messageId();
        String text = message.text() == null ? "" : message.text().trim();
        if (chatId == null || chatId.isBlank()) {
            return;
        }

        Optional<Long> userIdOpt = linkService.resolveUserId(chatId);
        if (userIdOpt.isEmpty()) {
            maxPort.sendMessage(chatId, UNLINKED_MESSAGE);
            return;
        }
        Long userId = userIdOpt.get();

        if (!checkRateLimit(userId, chatId)) {
            return;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }

        ParseResult result = parserService.parse(user, text);
        if (result.unparsed()) {
            maxPort.sendMessage(chatId, result.clarificationQuestion());
            return;
        }

        String payload = serialize(result.candidates());
        MaxPendingImport pending = pendingRepository.save(MaxPendingImport.builder()
                .chatId(chatId)
                .userId(userId)
                .messageId(messageId == null ? "" : messageId)
                .payload(payload)
                .build());
        maxPort.sendCard(chatId, renderCard(result.candidates()),
                ACCEPT_PREFIX + pending.getId(), REJECT_PREFIX + pending.getId());
    }

    /** Handle an inline-button tap (Принять/Отклонить). */
    @Transactional
    public void handleCallback(MaxCallbackQuery callback) {
        String data = callback.payload();
        String chatId = callback.chatId();
        String messageId = callback.messageId();
        String callbackId = callback.callbackId();
        if (data == null || chatId == null || messageId == null) {
            return;
        }
        if (data.startsWith(ACCEPT_PREFIX)) {
            accept(UUID.fromString(data.substring(ACCEPT_PREFIX.length())), chatId, messageId, callbackId);
        } else if (data.startsWith(REJECT_PREFIX)) {
            reject(UUID.fromString(data.substring(REJECT_PREFIX.length())), chatId, messageId, callbackId);
        }
    }

    private void accept(UUID pendingId, String chatId, String messageId, String callbackId) {
        MaxPendingImport pending = pendingRepository.findById(pendingId).orElse(null);
        if (pending == null) {
            return;
        }
        List<ParsedCandidate> candidates = deserialize(pending.getPayload());
        List<ConfirmCandidate> confirm = candidates.stream().map(this::toConfirm).toList();
        confirmService.confirm(userById(pending.getUserId()), new ConfirmImportRequest(confirm));
        pendingRepository.delete(pending);
        maxPort.editMessage(chatId, messageId, "Готово — записал в WOLF.");
        maxPort.answerCallback(callbackId);
    }

    private void reject(UUID pendingId, String chatId, String messageId, String callbackId) {
        pendingRepository.findById(pendingId).ifPresent(p -> {
            pendingRepository.delete(p);
            maxPort.editMessage(chatId, messageId, "Отклонено — ничего не создано.");
            maxPort.answerCallback(callbackId);
        });
    }

    private boolean checkRateLimit(Long userId, String chatId) {
        int limit = importBotProperties.getDailyLimitPerUser();
        if (limit <= 0) {
            return true;
        }
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        ImportBotDailyUsage usage = usageRepository.findByUserIdAndUsageDate(userId, today)
                .orElseGet(() -> ImportBotDailyUsage.builder()
                        .userId(userId).usageDate(today).requestCount(0).build());
        if (usage.getRequestCount() >= limit) {
            maxPort.sendMessage(chatId, LIMIT_MESSAGE);
            return false;
        }
        usage.setRequestCount(usage.getRequestCount() + 1);
        usageRepository.save(usage);
        return true;
    }

    private ConfirmCandidate toConfirm(ParsedCandidate candidate) {
        List<ParsedField> fields = candidate.fields().stream()
                .map(f -> new ParsedField(f.name(), f.value(), f.confidence()))
                .toList();
        return new ConfirmCandidate(candidate.kind(), fields);
    }

    private String renderCard(List<ParsedCandidate> candidates) {
        StringBuilder sb = new StringBuilder("Что записать в WOLF:\n");
        int i = 1;
        for (ParsedCandidate c : candidates) {
            String title = fieldValue(c, "title");
            EntityKind kind = c.kind();
            sb.append(i++).append(". ").append(kind).append(": ")
                    .append(title == null || title.isBlank() ? "(без названия)" : title).append('\n');
        }
        sb.append("\nПринять — запишу как есть. Поправить — откройте WOLF и измените в чат-панели импорта.");
        return sb.toString();
    }

    private static String fieldValue(ParsedCandidate c, String name) {
        return c.fields().stream()
                .filter(f -> f.name().equals(name))
                .map(ParsedField::value)
                .findFirst().orElse("");
    }

    private String serialize(List<ParsedCandidate> candidates) {
        try {
            return objectMapper.writeValueAsString(candidates);
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось сериализовать карточку импорта", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<ParsedCandidate> deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, new TypeReference<List<ParsedCandidate>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String userById(Long userId) {
        return userRepository.findById(userId)
                .map(User::getUsername)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));
    }
}
