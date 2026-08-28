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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.importer;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/**
 * Per-user daily request counter for the AI import bot rate limit
 * ({@code wolf.import-bot.daily-limit-per-user}). One row per user per UTC day;
 * the counter resets naturally on the next day's row.
 *
 * <p>Introduced by release 0.7 ticket 04 as a shared counter: every import
 * channel (chat-panel, Telegram, Max) increments the same per-user row so a
 * single {@code daily-limit-per-user} budget applies across all channels, not a
 * separate budget per channel. It replaces the Telegram-only
 * {@code telegram_daily_usage} table (renamed by migration V40).
 */
@Entity
@Table(name = "import_bot_daily_usage", uniqueConstraints = @UniqueConstraint(
        name = "uq_import_bot_daily_usage", columnNames = {"user_id", "usage_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportBotDailyUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "request_count", nullable = false)
    private int requestCount;
}
