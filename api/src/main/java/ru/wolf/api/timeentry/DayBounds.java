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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.timeentry;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Logical day bounds using user setting «Конец дня» ({@code dayEnd}).
 *
 * <p>Day D is half-open {@code [D + dayEnd, (D+1) + dayEnd)}.
 * Example: dayEnd=02:00 → 2026-04-10 = [2026-04-10T02:00, 2026-04-11T02:00).
 * Reading 00:30 on calendar 11 Apr still belongs to logical day 10 Apr.
 *
 * <p>dayEnd=00:00 → classic midnight bounds [D 00:00, D+1 00:00).
 */
public final class DayBounds {

    private DayBounds() {}

    public record Range(LocalDateTime start, LocalDateTime endExclusive) {
        public boolean contains(LocalDateTime t) {
            return !t.isBefore(start) && t.isBefore(endExclusive);
        }
    }

    public static Range forDay(LocalDate day, LocalTime dayEnd) {
        LocalTime boundary = dayEnd == null ? LocalTime.MIDNIGHT : dayEnd;
        LocalDateTime start = day.atTime(boundary);
        LocalDateTime end = day.plusDays(1).atTime(boundary);
        return new Range(start, end);
    }

    /** Wall-clock label for a slot inside a logical day (HH:mm). */
    public static LocalTime slotClock(LocalDateTime slotStart) {
        return slotStart.toLocalTime();
    }
}
