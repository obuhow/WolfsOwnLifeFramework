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

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bot API connection settings for the Max import channel (release 0.7, ticket 04).
 *
 * <p>{@code botUsername} is used to build the {@code max.ru/<bot>/?start=<token>}
 * deep link shown in Settings (Max generates the bot name as {@code id<ИНН>_bot}
 * — it cannot be chosen). {@code secretToken} guards the inbound webhook
 * ({@code X-Max-Bot-Api-Secret}) — independent of the app's JWT auth, matching the
 * ticket's requirement that the webhook verifies a dedicated secret, not a shared
 * JWT. {@code apiBase} defaults to the official {@code platform-api2.max.ru}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "wolf.max")
public class MaxProperties {
    private String botUsername = "";
    private String botToken = "";
    private String secretToken = "";
    private String apiBase = "https://platform-api2.max.ru";
}
