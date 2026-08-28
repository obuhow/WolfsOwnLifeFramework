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
 * Rate-limit settings for the AI import bot across all channels (ticket 03,
 * point 6). {@code dailyLimitPerUser} caps LLM parse requests per WOLF user
 * per UTC day; exceeding it yields a polite text refusal while the normal
 * chat-panel form stays available.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "wolf.import-bot")
public class ImportBotProperties {
    /** Max import-bot parse requests per user per day. 0 disables the limit. */
    private int dailyLimitPerUser = 20;
}
