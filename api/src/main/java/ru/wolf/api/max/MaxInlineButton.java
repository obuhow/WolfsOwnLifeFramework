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
package ru.wolf.api.max;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inline keyboard button payload sent to Max. {@code callbackData} is the opaque
 * string WOLF receives back on tap (WOLF action + pending import id). Mirrors
 * {@link ru.wolf.api.telegram.InlineButton}; Max's wire shape uses a
 * discriminated {@code type: "callback"} button with a {@code payload} field.
 */
public record MaxInlineButton(
        String text,
        @JsonProperty("callback_data") String callbackData
) {
    public static MaxInlineButton of(String text, String callbackData) {
        return new MaxInlineButton(text, callbackData);
    }

    /** Max wire representation: a callback button with its payload. */
    public record MaxButton(String type, String text, String payload) {
        public static MaxButton of(String text, String callbackData) {
            return new MaxButton("callback", text, callbackData);
        }
    }
}
