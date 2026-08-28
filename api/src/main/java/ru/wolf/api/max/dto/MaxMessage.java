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
package ru.wolf.api.max.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inbound Max message (the {@code message} of a {@code message_created} update).
 * Max nests the dialog id under {@code recipient.chat_id}; we expose it flat.
 *
 * @param body      message body — text and attachments
 * @param recipient recipient object carrying the chat id
 */
public record MaxMessage(
        MaxMessageBody body,
        MaxRecipient recipient
) {

    /** Dialog id this message came from (Max returns it as a number). */
    public String chatId() {
        return recipient != null ? recipient.chatId() : null;
    }

    /** Raw text of the message body (nullable for non-text content). */
    public String text() {
        return body != null ? body.text() : null;
    }

    /** Message id (used to edit the card on accept/reject). */
    public String messageId() {
        return body != null ? body.mid() : null;
    }

    /** Minimal recipient object (only chat id is consumed). */
    public record MaxRecipient(@JsonProperty("chat_id") String chatId) {
    }

    /** Message body (only the text + mid fields are consumed). */
    public record MaxMessageBody(
            @JsonProperty("mid") String mid,
            String text
    ) {
    }
}
