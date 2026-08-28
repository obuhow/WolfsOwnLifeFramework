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
package ru.wolf.api.telegram.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inbound inline-button tap from a Telegram card.
 *
 * @param id          callback query id (passed back to answerCallbackQuery)
 * @param chatId      chat the button belongs to
 * @param messageId   message id the button is attached to
 * @param fromChatId  chat id of the user who tapped
 * @param data        button payload (WOLF action + pending id)
 */
public record TelegramCallbackQuery(
        String id,
        TelegramChat chat,
        @JsonProperty("message") TelegramCallbackMessage message,
        @JsonProperty("from") TelegramFrom from,
        String data
) {

    /** Telegram nests chat under {@code message.chat} for callback queries. */
    public String chatId() {
        if (chat != null && chat.id() != null) return chat.id();
        return message != null && message.chat() != null ? message.chat().id() : null;
    }

    public Long messageId() {
        return message != null ? message.messageId() : null;
    }

    /** Originating user's chat id (same as chatId for private chats). */
    public String fromChatId() {
        return from != null ? from.id() : null;
    }

    public record TelegramChat(@JsonProperty("id") String id) {
    }

    public record TelegramFrom(@JsonProperty("id") String id) {
    }

    public record TelegramCallbackMessage(
            @JsonProperty("message_id") Long messageId,
            @JsonProperty("chat") TelegramChat chat
    ) {
    }
}
