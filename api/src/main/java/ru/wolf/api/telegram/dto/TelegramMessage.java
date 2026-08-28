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
 * Inbound Telegram message.
 *
 * @param messageId identifier of this message (used to answer inline buttons)
 * @param chatId    chat the message came from
 * @param text      raw text (may be {@code null} for non-text content)
 */
public record TelegramMessage(
        @JsonProperty("message_id") Long messageId,
        @JsonProperty("chat") TelegramChat chat,
        String text
) {

    /** Telegram nests chat id under a {@code chat} object; expose it flat. */
    public String chatId() {
        return chat != null ? chat.id() : null;
    }

    /** Minimal nested chat object (only id is consumed). */
    public record TelegramChat(@JsonProperty("id") String id) {
    }
}
