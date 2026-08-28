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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.wolf.api.importer.ImportConfirmService;
import ru.wolf.api.importer.ImportParserService;
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
 * DB-free unit tests for the Telegram import channel (release 0.7, ticket 03).
 *
 * <p>Covers the ticket's Testing Decisions: (a) an unlinked chat creates nothing,
 * (b) chat A cannot affect user B's data, (c) daily limit yields a polite refusal
 * and resets conceptually on the next day's row. The shared parser/confirm path
 * is mocked; isolation comes from resolving {@code userId} solely by {@code chat_id}.
 */
@ExtendWith(MockitoExtension.class)
class TelegramImportServiceTest {

    @Mock private TelegramLinkService linkService;
    @Mock private ImportParserService parserService;
    @Mock private ImportConfirmService confirmService;
    @Mock private TelegramPendingImportRepository pendingRepository;
    @Mock private TelegramDailyUsageRepository usageRepository;
    @Mock private TelegramPort telegramPort;
    @Mock private UserRepository userRepository;

    private TelegramImportService service;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ImportBotProperties props = new ImportBotProperties();

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        props.setDailyLimitPerUser(2);
        service = new TelegramImportService(linkService, parserService, confirmService,
                pendingRepository, usageRepository, telegramPort, userRepository, props, objectMapper);
        userA = User.builder().id(1L).username("alice").timezone("Europe/Moscow").build();
        userB = User.builder().id(2L).username("bob").timezone("Europe/Moscow").build();
    }

    private TelegramMessage msg(String chatId, String text) {
        return new TelegramMessage(10L,
                new TelegramMessage.TelegramChat(chatId), text);
    }

    private ParseResult parsed() {
        List<ParsedField> fields = List.of(ParsedField.confident("title", "тренировка"));
        return ParseResult.parsed(List.of(new ParsedCandidate(EntityKind.DELO, fields)), List.of());
    }

    // --- Isolation: unlinked chat creates nothing ---

    @Test
    void unlinkedChat_doesNotCreateEntities() {
        when(linkService.resolveUserId("chatX")).thenReturn(Optional.empty());

        service.handleMessage(msg("chatX", "завтра тренировка в 10:00"));

        verify(parserService, never()).parse(any(), anyString());
        verify(confirmService, never()).confirm(anyString(), any(ConfirmImportRequest.class));
        verify(telegramPort).sendMessage(eq("chatX"), contains("не привязан"));
    }

    // --- Isolation: chat A cannot touch user B ---

    @Test
    void chatA_textOnlyResolvesUserA_neverTouchesUserB() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(userA));
        when(linkService.resolveUserId("chatA")).thenReturn(Optional.of(1L));
        when(usageRepository.findByUserIdAndUsageDate(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        TelegramPendingImport pending = TelegramPendingImport.builder()
                .id(java.util.UUID.randomUUID()).chatId("chatA").userId(1L)
                .payload("[]").build();
        when(pendingRepository.save(any())).thenReturn(pending);
        when(parserService.parse(eq(userA), anyString())).thenReturn(parsed());

        service.handleMessage(msg("chatA", "завтра тренировка"));

        verify(parserService).parse(eq(userA), anyString());
        verify(parserService, never()).parse(eq(userB), anyString());
        verify(confirmService, never()).confirm(eq("bob"), any());
        verify(telegramPort).sendCard(eq("chatA"), anyString(), anyString(), anyString());
    }

    @Test
    void acceptCallback_onlyCreatesForPendingOwner() {
        java.util.UUID pid = java.util.UUID.randomUUID();
        TelegramPendingImport pending = TelegramPendingImport.builder()
                .id(pid).chatId("chatA").userId(1L)
                .payload(toJson(List.of(ParsedField.confident("title", "тренировка")), EntityKind.DELO))
                .build();
        when(pendingRepository.findById(pid)).thenReturn(Optional.of(pending));
        when(userRepository.findById(1L)).thenReturn(Optional.of(userA));

        TelegramCallbackQuery cb = new TelegramCallbackQuery("cb1",
                new TelegramCallbackQuery.TelegramChat("chatA"),
                new TelegramCallbackQuery.TelegramCallbackMessage(99L,
                        new TelegramCallbackQuery.TelegramChat("chatA")),
                new TelegramCallbackQuery.TelegramFrom("chatA"),
                "accept:" + pid);
        service.handleCallback(cb);

        verify(confirmService).confirm(eq("alice"), any(ConfirmImportRequest.class));
        verify(confirmService, never()).confirm(eq("bob"), any());
        verify(pendingRepository).delete(pending);
    }

    // --- Rate limit ---

    @Test
    void dailyLimitExceeded_politeRefusal_noParse() {
        when(linkService.resolveUserId("chatA")).thenReturn(Optional.of(1L));
        TelegramDailyUsage usage = TelegramDailyUsage.builder()
                .userId(1L).usageDate(LocalDate.now(java.time.ZoneId.of("UTC"))).requestCount(2).build();
        when(usageRepository.findByUserIdAndUsageDate(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(usage));

        service.handleMessage(msg("chatA", "ещё одна задача"));

        verify(parserService, never()).parse(any(), anyString());
        verify(confirmService, never()).confirm(anyString(), any());
        verify(telegramPort).sendMessage(eq("chatA"), contains("лимит"));
    }

    @Test
    void withinLimit_incrementsCounter_andParses() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(userA));
        when(linkService.resolveUserId("chatA")).thenReturn(Optional.of(1L));
        TelegramDailyUsage usage = TelegramDailyUsage.builder()
                .userId(1L).usageDate(LocalDate.now(java.time.ZoneId.of("UTC"))).requestCount(1).build();
        when(usageRepository.findByUserIdAndUsageDate(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(usage));
        TelegramPendingImport pending = TelegramPendingImport.builder()
                .id(java.util.UUID.randomUUID()).chatId("chatA").userId(1L).payload("[]").build();
        when(pendingRepository.save(any())).thenReturn(pending);
        when(parserService.parse(eq(userA), anyString())).thenReturn(parsed());

        service.handleMessage(msg("chatA", "задача в 9:00"));

        verify(usageRepository).save(any(TelegramDailyUsage.class));
        verify(parserService).parse(eq(userA), anyString());
        verify(telegramPort).sendCard(eq("chatA"), anyString(), anyString(), anyString());
    }

    private String toJson(List<ParsedField> fields, EntityKind kind) {
        try {
            return objectMapper.writeValueAsString(
                    List.of(new ParsedCandidate(kind, fields)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
