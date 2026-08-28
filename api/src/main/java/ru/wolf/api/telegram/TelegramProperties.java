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

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bot API connection settings for the Telegram import channel (ticket 03).
 *
 * <p>{@code botUsername} is used to build the {@code t.me/<bot>/?start=<token>}
 * deep link shown in Settings. {@code secretToken} guards the inbound webhook
 * ({@code X-Telegram-Bot-Api-Secret-Token}) — it is independent of the app's
 * JWT auth, matching the ticket's requirement that the webhook verifies a
 * dedicated secret, not a shared JWT.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "wolf.telegram")
public class TelegramProperties {
    private String botUsername = "";
    private String botToken = "";
    private String secretToken = "";
    private String apiBase = "https://api.telegram.org";
}
