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
package ru.wolf.api.max.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Minimal inbound Max Update shape (only the fields WOLF consumes from a
 * webhook). The Bot API is a flat oneOf over {@code update_type}; we read
 * selectively (only {@code message_created} / {@code bot_started} /
 * {@code message_callback} are needed).
 *
 * @param updateType  discriminator ({@code message_created}, {@code bot_started}, ...)
 * @param message     non-callback message (text / {@code /start} payload)
 * @param callback    inline-button tap (Принять/Отклонить)
 * @param chatId      present on {@code bot_started} (dialog id where the event occurred)
 * @param payload     deep-link payload from the {@code bot_started} event
 */
public record MaxUpdate(
        @JsonProperty("update_type") String updateType,
        MaxMessage message,
        MaxCallbackQuery callback,
        @JsonProperty("chat_id") String chatId,
        String payload
) {

    /** True when the update is a {@code /start <token>} deep-link bind. */
    public boolean isBotStarted() {
        return "bot_started".equals(updateType);
    }

    /** True when the update is a new inbound text message. */
    public boolean isMessageCreated() {
        return "message_created".equals(updateType);
    }

    /** True when the update is an inline-button tap. */
    public boolean isMessageCallback() {
        return "message_callback".equals(updateType);
    }
}
