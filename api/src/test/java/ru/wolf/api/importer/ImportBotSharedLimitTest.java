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
package ru.wolf.api.importer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.wolf.api.importer.dto.ConfirmImportRequest;
import ru.wolf.api.importer.dto.EntityKind;
import ru.wolf.api.importer.dto.ParseResult;
import ru.wolf.api.importer.dto.ParsedCandidate;
import ru.wolf.api.importer.dto.ParsedField;
import ru.wolf.api.max.MaxImportService;
import ru.wolf.api.max.MaxLinkService;
import ru.wolf.api.max.dto.MaxMessage;
import ru.wolf.api.max.MaxPendingImportRepository;
import ru.wolf.api.max.MaxPort;
import ru.wolf.api.max.dto.MaxCallbackQuery;
import ru.wolf.api.max.dto.MaxMessage.MaxMessageBody;
import ru.wolf.api.max.dto.MaxMessage.MaxRecipient;
import ru.wolf.api.telegram.TelegramImportService;
import ru.wolf.api.telegram.TelegramLinkService;
import ru.wolf.api.telegram.dto.TelegramMessage;
import ru.wolf.api.telegram.TelegramPendingImportRepository;
import ru.wolf.api.telegram.TelegramPort;
import ru.wolf.api.telegram.dto.TelegramCallbackQuery;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

/**
 * Release 0.7, ticket 04, point 5: the daily import-bot limit is a SINGLE shared
 * counter per user across all channels (chat-panel, Telegram, Max). This test wires
 * both the Telegram and Max import services to the SAME mocked
 * {@link ImportBotRateLimitService} and asserts that requests from the two
 * channels by one user consume the same per-user budget.
 *
 * <p>With {@code daily-limit-per-user = 2}: one Telegram request + one Max request
 * leaves exactly one slot; a third request (from either channel) is refused
 * politely without parsing.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImportBotSharedLimitTest {

    @Mock private ImportBotRateLimitService sharedRateLimit;
    @Mock private TelegramLinkService telegramLink;
    @Mock private MaxLinkService maxLink;
    @Mock private ImportParserService parserService;
    @Mock private ImportConfirmService confirmService;
    @Mock private TelegramPendingImportRepository telegramPending;
    @Mock private MaxPendingImportRepository maxPending;
    @Mock private TelegramPort telegramPort;
    @Mock private MaxPort maxPort;
    @Mock private UserRepository userRepository;

    private TelegramImportService telegramService;
    private MaxImportService maxService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private User user = User.builder().id(1L).username("alice").timezone("Europe/Moscow").build();

    @BeforeEach
    void setUp() {
        telegramService = new TelegramImportService(telegramLink, parserService, confirmService,
                telegramPending, sharedRateLimit, telegramPort, userRepository, objectMapper);
        maxService = new MaxImportService(maxLink, parserService, confirmService,
                maxPending, sharedRateLimit, maxPort, userRepository, objectMapper);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(telegramLink.resolveUserId("chatT")).thenReturn(Optional.of(1L));
        when(maxLink.resolveUserId("chatM")).thenReturn(Optional.of(1L));
        when(parserService.parse(any(User.class), anyString())).thenReturn(ParseResult.parsed(
                List.of(new ParsedCandidate(EntityKind.DELO,
                        List.of(ParsedField.confident("title", "тренировка")))), List.of()));
        when(telegramPending.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(maxPending.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void telegramAndMaxShareOneBudget() {
        when(sharedRateLimit.tryConsume(1L)).thenReturn(true, true, false);

        // 1st request via Telegram
        telegramService.handleMessage(new TelegramMessage(1L,
                new TelegramMessage.TelegramChat("chatT"), "тренировка в 10:00"));
        // 2nd request via Max
        maxService.handleMessage(new MaxMessage(new MaxMessageBody("mM", "пробежка в 7:00"),
                new MaxRecipient("chatM")));

        // Both should have parsed and incremented the shared counter (now 2).
        verify(parserService, times(2)).parse(eq(user), anyString());
        verify(sharedRateLimit, times(2)).tryConsume(1L);

        // 3rd request (back to Telegram) must be refused — shared budget exhausted.
        telegramService.handleMessage(new TelegramMessage(2L,
                new TelegramMessage.TelegramChat("chatT"), "ещё задача"));
        verify(parserService, times(2)).parse(any(), anyString()); // no third parse
        verify(telegramPort).sendMessage(eq("chatT"), contains("лимит"));
    }

    @Test
    void isolatedCounterPerUser_notCrossChannelAcrossUsers() {
        // A different user has their own row; their Max request must not touch user 1's budget.
        when(maxLink.resolveUserId("chatM2")).thenReturn(Optional.of(2L));
        when(userRepository.findById(2L)).thenReturn(Optional.of(
                User.builder().id(2L).username("bob").timezone("Europe/Moscow").build()));

        when(sharedRateLimit.tryConsume(2L)).thenReturn(true);

        maxService.handleMessage(new MaxMessage(new MaxMessageBody("m2", "задача B"),
                new MaxRecipient("chatM2")));

        verify(sharedRateLimit).tryConsume(2L);
        verify(parserService, never()).parse(eq(user), anyString()); // user 1 never parsed
        verify(parserService).parse(any(User.class), anyString());
    }
}
