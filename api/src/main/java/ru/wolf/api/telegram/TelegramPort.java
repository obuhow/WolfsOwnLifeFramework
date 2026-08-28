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

/**
 * Outbound Telegram transport (the bot → Telegram API side). The import
 * channel uses this only to deliver replies — parse/confirm logic lives in the
 * services. Two adapters implement it: {@code HttpTelegramAdapter} (prod) and
 * {@code FakeTelegramAdapter} (test profile), mirroring the note/assistant
 * Ports &amp; Adapters split already in the codebase.
 */
public interface TelegramPort {

    /** Send a plain text reply to a chat. No-op is never silent on error. */
    void sendMessage(String chatId, String text);

    /** Reply with a card preview plus Принять/Отклонить inline buttons. */
    void sendCard(String chatId, String text, String callbackDataAccept, String callbackDataReject);

    /** Acknowledge an inline-button tap (stops the loading state). */
    void answerCallback(String callbackQueryId);

    /** Replace a previously sent card with a final status line. */
    void editMessage(String chatId, long messageId, String text);
}
