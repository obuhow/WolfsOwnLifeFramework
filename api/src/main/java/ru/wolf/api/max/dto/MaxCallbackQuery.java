/*
 * WOLF — Wolf's Own Life Framework
 * Copyright (C) 2025 Pavel Obukhov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it be useful,
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
 * Inbound inline-button tap from a Max card ({@code message_callback} update).
 *
 * @param callbackId  button keyboard identifier, passed back to {@code POST /answers}
 * @param payload     button payload (WOLF action + pending import id)
 * @param message     original message carrying the inline keyboard
 */
public record MaxCallbackQuery(
        @JsonProperty("callback_id") String callbackId,
        String payload,
        MaxCallbackMessage message
) {

    /** Chat id of the dialog the button belongs to. */
    public String chatId() {
        return message != null ? message.chatId() : null;
    }

    /** Message id the button is attached to. */
    public String messageId() {
        return message != null ? message.messageId() : null;
    }

    /** Original message carrying the inline keyboard. */
    public record MaxCallbackMessage(
            @JsonProperty("body") MaxCallbackBody body,
            MaxMessage.MaxRecipient recipient
    ) {
        public String chatId() {
            return recipient != null ? recipient.chatId() : null;
        }

        public String messageId() {
            return body != null ? body.mid() : null;
        }

        public record MaxCallbackBody(@JsonProperty("mid") String mid) {
        }
    }
}
