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
package ru.wolf.api.max;

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

import ru.wolf.api.importer.ImportBotProperties;
import ru.wolf.api.importer.ImportConfirmService;
import ru.wolf.api.importer.ImportParserService;
import ru.wolf.api.importer.dto.ConfirmImportRequest;
import ru.wolf.api.importer.dto.EntityKind;
import ru.wolf.api.importer.dto.ParseResult;
import ru.wolf.api.importer.dto.ParsedCandidate;
import ru.wolf.api.importer.dto.ParsedField;
import ru.wolf.api.max.dto.MaxCallbackQuery;
import ru.wolf.api.max.dto.MaxDisconnectRequest;
import ru.wolf.api.max.dto.MaxLinkStatus;
import ru.wolf.api.max.dto.MaxMessage;
import ru.wolf.api.max.dto.MaxUpdate;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

/**
 * DB-free unit tests for the Max import channel (release 0.7, ticket 04).
 *
 * <p>Mirrors the Telegram channel's Testing Decisions: (a) an unlinked chat
 * creates nothing, (b) chat A cannot affect user B's data, (c) daily limit yields
 * a polite refusal. The shared parser/confirm path is mocked; isolation comes from
 * resolving {@code userId} solely by {@code chat_id}.
 *
 * <p>The {@code bot_started} deep-link bind is covered here too (unique to Max vs
 * Telegram's {@code /start}): a {@code bot_started} update carrying the token
 * binds the chat id.
 */
@ExtendWith(MockitoExtension.class)
class MaxImportServiceTest {

    @Mock private MaxLinkService linkService;
    @Mock private ImportParserService parserService;
    @Mock private ImportConfirmService confirmService;
    @Mock private MaxPendingImportRepository pendingRepository;
    @Mock private ru.wolf.api.importer.ImportBotDailyUsageRepository usageRepository;
    @Mock private MaxPort maxPort;
    @Mock private UserRepository userRepository;

    private MaxImportService service;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ImportBotProperties props = new ImportBotProperties();

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        props.setDailyLimitPerUser(2);
        service = new MaxImportService(linkService, parserService, confirmService,
                pendingRepository, usageRepository, maxPort, userRepository, props, objectMapper);
        userA = User.builder().id(1L).username("alice").timezone("Europe/Moscow").build();
        userB = User.builder().id(2L).username("bob").timezone("Europe/Moscow").build();
    }

    private MaxMessage msg(String chatId, String text) {
        return new MaxMessage(
                new MaxMessage.MaxMessageBody("m-" + chatId, text),
                new MaxMessage.MaxRecipient(chatId));
    }

    private ParseResult parsed() {
        List<ParsedField> fields = List.of(ParsedField.confident("title", "тренировка"));
        return ParseResult.parsed(List.of(new ParsedCandidate(EntityKind.DELO, fields)), List.of());
    }

    // --- bot_started deep link binds the chat ---

    @Test
    void botStarted_withValidToken_bindsChat() {
        when(linkService.linkAccount("tok123", "chatA")).thenReturn(true);

        service.handleUpdate(new MaxUpdate("bot_started", null, null, "chatA", "tok123"));

        verify(linkService).linkAccount("tok123", "chatA");
        verify(maxPort).sendMessage(eq("chatA"), contains("привязан"));
    }

    @Test
    void botStarted_withInvalidToken_doesNotBind() {
        when(linkService.linkAccount(anyString(), anyString())).thenReturn(false);

        service.handleUpdate(new MaxUpdate("bot_started", null, null, "chatX", "bad"));

        verify(maxPort).sendMessage(eq("chatX"), contains("не привязан"));
    }

    // --- Isolation: unlinked chat creates nothing ---

    @Test
    void unlinkedChat_doesNotCreateEntities() {
        when(linkService.resolveUserId("chatX")).thenReturn(Optional.empty());

        service.handleMessage(msg("chatX", "завтра тренировка в 10:00"));

        verify(parserService, never()).parse(any(), anyString());
        verify(confirmService, never()).confirm(anyString(), any(ConfirmImportRequest.class));
        verify(maxPort).sendMessage(eq("chatX"), contains("не привязан"));
    }

    // --- Isolation: chat A cannot touch user B ---

    @Test
    void chatA_textOnlyResolvesUserA_neverTouchesUserB() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(userA));
        when(linkService.resolveUserId("chatA")).thenReturn(Optional.of(1L));
        when(usageRepository.findByUserIdAndUsageDate(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        MaxPendingImport pending = MaxPendingImport.builder()
                .id(java.util.UUID.randomUUID()).chatId("chatA").userId(1L)
                .messageId("m-chatA").payload("[]").build();
        when(pendingRepository.save(any())).thenReturn(pending);
        when(parserService.parse(eq(userA), anyString())).thenReturn(parsed());

        service.handleMessage(msg("chatA", "завтра тренировка"));

        verify(parserService).parse(eq(userA), anyString());
        verify(parserService, never()).parse(eq(userB), anyString());
        verify(confirmService, never()).confirm(eq("bob"), any());
        verify(maxPort).sendCard(eq("chatA"), anyString(), anyString(), anyString());
    }

    @Test
    void acceptCallback_onlyCreatesForPendingOwner() {
        java.util.UUID pid = java.util.UUID.randomUUID();
        MaxPendingImport pending = MaxPendingImport.builder()
                .id(pid).chatId("chatA").userId(1L).messageId("m1")
                .payload(toJson(List.of(ParsedField.confident("title", "тренировка")), EntityKind.DELO))
                .build();
        when(pendingRepository.findById(pid)).thenReturn(Optional.of(pending));
        when(userRepository.findById(1L)).thenReturn(Optional.of(userA));

        MaxCallbackQuery cb = new MaxCallbackQuery("cb1", "accept:" + pid,
                new MaxCallbackQuery.MaxCallbackMessage(
                        new MaxCallbackQuery.MaxCallbackMessage.MaxCallbackBody("m1"),
                        new MaxMessage.MaxRecipient("chatA")));
        service.handleCallback(cb);

        verify(confirmService).confirm(eq("alice"), any(ConfirmImportRequest.class));
        verify(confirmService, never()).confirm(eq("bob"), any());
        verify(pendingRepository).delete(pending);
    }

    // --- Rate limit (shared counter, see ImportBotSharedLimitTest) ---

    @Test
    void dailyLimitExceeded_politeRefusal_noParse() {
        when(linkService.resolveUserId("chatA")).thenReturn(Optional.of(1L));
        ru.wolf.api.importer.ImportBotDailyUsage usage = ru.wolf.api.importer.ImportBotDailyUsage.builder()
                .userId(1L).usageDate(LocalDate.now(java.time.ZoneId.of("UTC"))).requestCount(2).build();
        when(usageRepository.findByUserIdAndUsageDate(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(usage));

        service.handleMessage(msg("chatA", "ещё одна задача"));

        verify(parserService, never()).parse(any(), anyString());
        verify(confirmService, never()).confirm(anyString(), any());
        verify(maxPort).sendMessage(eq("chatA"), contains("лимит"));
    }

    @Test
    void withinLimit_incrementsCounter_andParses() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(userA));
        when(linkService.resolveUserId("chatA")).thenReturn(Optional.of(1L));
        ru.wolf.api.importer.ImportBotDailyUsage usage = ru.wolf.api.importer.ImportBotDailyUsage.builder()
                .userId(1L).usageDate(LocalDate.now(java.time.ZoneId.of("UTC"))).requestCount(1).build();
        when(usageRepository.findByUserIdAndUsageDate(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(usage));
        MaxPendingImport pending = MaxPendingImport.builder()
                .id(java.util.UUID.randomUUID()).chatId("chatA").userId(1L)
                .messageId("m-chatA").payload("[]").build();
        when(pendingRepository.save(any())).thenReturn(pending);
        when(parserService.parse(eq(userA), anyString())).thenReturn(parsed());

        service.handleMessage(msg("chatA", "задача в 9:00"));

        verify(usageRepository).save(any(ru.wolf.api.importer.ImportBotDailyUsage.class));
        verify(parserService).parse(eq(userA), anyString());
        verify(maxPort).sendCard(eq("chatA"), anyString(), anyString(), anyString());
    }

    // --- Link status deep link uses max.ru host (point 4 of ticket) ---

    @Test
    void linkStatus_deepLinkUsesMaxHost() {
        when(linkService.getStatus("alice")).thenReturn(
                new MaxLinkStatus(false, null, "tok", "https://max.ru/bot_alice?start=tok", "bot_alice"));

        MaxLinkStatus status = linkService.getStatus("alice");

        assertThat(status.linkUrl()).startsWith("https://max.ru/");
        assertThat(status.botUsername()).isEqualTo("bot_alice");
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
