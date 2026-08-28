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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inline keyboard button payload sent to Telegram. {@code callback_data} is the
 * opaque string WOLF receives back on tap (WOLF action + pending import id).
 */
public record InlineButton(
        String text,
        @JsonProperty("callback_data") String callbackData
) {
    public static InlineButton of(String text, String callbackData) {
        return new InlineButton(text, callbackData);
    }
}
