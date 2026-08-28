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
import ru.wolf.api.importer.ImportConfirmService;
import ru.wolf.api.importer.ImportParserService;
import ru.wolf.api.importer.dto.ConfirmCandidate;
import ru.wolf.api.importer.dto.ConfirmImportRequest;
import ru.wolf.api.importer.dto.EntityKind;
import ru.wolf.api.importer.dto.ParseResult;
import ru.wolf.api.importer.dto.ParsedCandidate;
import ru.wolf.api.importer.dto.ParsedField;
import ru.wolf.api.telegram.dto.TelegramCallbackQuery;
import ru.wolf.api.telegram.dto.TelegramMessage;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

/**
 * Telegram import channel handler (ticket 03). Translates an inbound Telegram
 * update into WOLF entities through the shared {@link ImportParserService} —
 * the bot path carries no {@code SecurityContext} and never assumes the
 * sender's identity: the WOLF {@code userId} is resolved solely from the
 * {@code telegram_link} table by {@code chat_id}.
 *
 * <p>Per-message flow:
 * <ul>
 *   <li>{@code /start <token>} → bind account (delegated to
 *       {@link TelegramLinkService});</li>
 *   <li>free text → parse → if unparsed, a single clarifying question (no
 *       entities); otherwise a preview card with Принять/Отклонить inline
 *       buttons. Field editing is NOT offered in Telegram (only in the
 *       chat-panel channel) — reject with "Поправь в WOLF" + web link;</li>
 *   <li>inline tap → confirm (create entities via {@link ImportConfirmService})
 *       or reject (discard the pending row).</li>
 * </ul>
 *
 * <p>The bot never initiates a conversation: every outbound message here is a
 * direct reply to an inbound update.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramImportService {

    private static final String ACCEPT_PREFIX = "accept:";
    private static final String REJECT_PREFIX = "reject:";
    private static final String LIMIT_MESSAGE =
            "На сегодня лимит импорта исчерпан — попробуйте завтра или сохраните текст как заметку.";
    private static final String REJECT_EDIT_HINT =
            "Не разобрал точно — поправьте в WOLF: ";
    private static final String UNLINKED_MESSAGE =
            "Этот аккаунт ещё не привязан. Откройте «Импорт из Telegram» в Настройках WOLF и нажмите «Привязать Telegram».";

    private final TelegramLinkService linkService;
    private final ImportParserService parserService;
    private final ImportConfirmService confirmService;
    private final TelegramPendingImportRepository pendingRepository;
    private final TelegramDailyUsageRepository usageRepository;
    private final TelegramPort telegramPort;
    private final UserRepository userRepository;
    private final ImportBotProperties importBotProperties;
    private final ObjectMapper objectMapper;

    /** Handle an inbound text message (parse → card, or clarify, or link hint). */
    @Transactional
    public void handleMessage(TelegramMessage message) {
        String chatId = message.chatId();
        String text = message.text() == null ? "" : message.text().trim();
        if (chatId == null || chatId.isBlank()) {
            return;
        }

        if (text.startsWith("/start")) {
            String token = extractStartToken(text);
            boolean ok = linkService.linkAccount(token, chatId);
            if (ok) {
                telegramPort.sendMessage(chatId, "Telegram привязан к вашему аккаунту WOLF. "
                        + "Присылайте заметки — я разберу их на Дела и Проекты.");
            } else {
                telegramPort.sendMessage(chatId, UNLINKED_MESSAGE);
            }
            return;
        }

        Optional<Long> userIdOpt = linkService.resolveUserId(chatId);
        if (userIdOpt.isEmpty()) {
            telegramPort.sendMessage(chatId, UNLINKED_MESSAGE);
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
            // Single clarifying question; nothing created.
            telegramPort.sendMessage(chatId, result.clarificationQuestion());
            return;
        }

        String payload = serialize(result.candidates());
        TelegramPendingImport pending = pendingRepository.save(TelegramPendingImport.builder()
                .chatId(chatId)
                .userId(userId)
                .payload(payload)
                .build());
        telegramPort.sendCard(chatId, renderCard(result.candidates()),
                ACCEPT_PREFIX + pending.getId(), REJECT_PREFIX + pending.getId());
    }

    /** Handle an inline-button tap (Принять/Отклонить). */
    @Transactional
    public void handleCallback(TelegramCallbackQuery callback) {
        String data = callback.data();
        String chatId = callback.chatId();
        Long messageId = callback.messageId();
        if (data == null || chatId == null || messageId == null) {
            return;
        }
        if (data.startsWith(ACCEPT_PREFIX)) {
            accept(UUID.fromString(data.substring(ACCEPT_PREFIX.length())), chatId, messageId, callback.id());
        } else if (data.startsWith(REJECT_PREFIX)) {
            reject(UUID.fromString(data.substring(REJECT_PREFIX.length())), chatId, messageId, callback.id());
        }
    }

    private void accept(UUID pendingId, String chatId, long messageId, String callbackQueryId) {
        TelegramPendingImport pending = pendingRepository.findById(pendingId).orElse(null);
        if (pending == null) {
            return;
        }
        List<ParsedCandidate> candidates = deserialize(pending.getPayload());
        List<ConfirmCandidate> confirm = candidates.stream().map(this::toConfirm).toList();
        confirmService.confirm(userById(pending.getUserId()), new ConfirmImportRequest(confirm));
        pendingRepository.delete(pending);
        telegramPort.editMessage(chatId, messageId, "Готово — записал в WOLF.");
        telegramPort.answerCallback(callbackQueryId);
    }

    private void reject(UUID pendingId, String chatId, long messageId, String callbackQueryId) {
        pendingRepository.findById(pendingId).ifPresent(p -> {
            pendingRepository.delete(p);
            telegramPort.editMessage(chatId, messageId, "Отклонено — ничего не создано.");
            telegramPort.answerCallback(callbackQueryId);
        });
    }

    private boolean checkRateLimit(Long userId, String chatId) {
        int limit = importBotProperties.getDailyLimitPerUser();
        if (limit <= 0) {
            return true;
        }
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        TelegramDailyUsage usage = usageRepository.findByUserIdAndUsageDate(userId, today)
                .orElseGet(() -> TelegramDailyUsage.builder()
                        .userId(userId).usageDate(today).requestCount(0).build());
        if (usage.getRequestCount() >= limit) {
            telegramPort.sendMessage(chatId, LIMIT_MESSAGE);
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

    private static String extractStartToken(String text) {
        String[] parts = text.split("\\s+", 2);
        return parts.length == 2 ? parts[1].trim() : "";
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
