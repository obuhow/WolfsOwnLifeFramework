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

/**
 * State of the current user's Telegram link, returned to SettingsView.
 *
 * @param linked         true when a {@code telegram_link} row exists for the user
 * @param chatId         the linked Telegram chat id (null when not linked)
 * @param pendingToken   a freshly generated one-time link token presented for copying
 * @param linkUrl        {@code https://t.me/<bot_username>?start=<token>} for the user to open
 * @param botUsername    configured bot username (empty until server config is set)
 */
public record TelegramLinkStatus(
        boolean linked,
        String chatId,
        String pendingToken,
        String linkUrl,
        String botUsername
) {
}
