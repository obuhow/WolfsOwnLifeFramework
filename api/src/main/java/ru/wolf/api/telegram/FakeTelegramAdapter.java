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

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Test double for {@link TelegramPort}. Records every outbound call so unit
 * tests can assert isolation (which chat got which message) without touching
 * the real Bot API. Active only under the {@code test} profile.
 */
@Component
@Profile("test")
@RequiredArgsConstructor
public class FakeTelegramAdapter implements TelegramPort {

    private final TelegramMessageCollector collector;

    @Override
    public void sendMessage(String chatId, String text) {
        collector.sent(chatId, text);
    }

    @Override
    public void sendCard(String chatId, String text, String callbackDataAccept, String callbackDataReject) {
        collector.card(chatId, text);
    }

    @Override
    public void answerCallback(String callbackQueryId) {
        // no-op in tests
    }

    @Override
    public void editMessage(String chatId, long messageId, String text) {
        collector.edited(chatId, text);
    }

    /** Captured outbound traffic, queryable from tests. */
    @Component
    @RequiredArgsConstructor
    static class TelegramMessageCollector {
        @Getter
        private final java.util.List<String> plain = new java.util.ArrayList<>();
        @Getter
        private final java.util.List<String> cards = new java.util.ArrayList<>();
        @Getter
        private final java.util.List<String> edits = new java.util.ArrayList<>();

        void sent(String chatId, String text) {
            plain.add(chatId + "::" + text);
        }

        void card(String chatId, String text) {
            cards.add(chatId + "::" + text);
        }

        void edited(String chatId, String text) {
            edits.add(chatId + "::" + text);
        }
    }
}
