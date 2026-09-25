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

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Shared per-user daily import-bot usage repository (release 0.7, ticket 04).
 * Replaces {@code TelegramDailyUsageRepository} — both Telegram and Max channels
 * increment the same per-user counter.
 */
@Repository
public interface ImportBotDailyUsageRepository extends JpaRepository<ImportBotDailyUsage, Long> {
    Optional<ImportBotDailyUsage> findByUserIdAndUsageDate(Long userId, LocalDate usageDate);

    @Query("SELECT COALESCE(SUM(u.requestCount), 0) FROM ImportBotDailyUsage u WHERE u.userId = :userId")
    long totalCount(@Param("userId") Long userId);

    /**
     * Atomically consumes one request from the shared daily budget. PostgreSQL's
     * upsert closes the check-then-increment race between concurrent requests;
     * zero rows means the existing counter has already reached the limit.
     */
    @Modifying
    @Query(value = """
            INSERT INTO import_bot_daily_usage (user_id, usage_date, request_count)
            VALUES (:userId, :usageDate, 1)
            ON CONFLICT (user_id, usage_date) DO UPDATE
            SET request_count = import_bot_daily_usage.request_count + 1
            WHERE import_bot_daily_usage.request_count < :limit
            """, nativeQuery = true)
    int tryConsume(@Param("userId") Long userId,
                   @Param("usageDate") LocalDate usageDate,
                   @Param("limit") int limit);
}
