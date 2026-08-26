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

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Ночные часы: half-open wall-clock window on the daily cycle.
 *
 * <ul>
 *   <li>{@code nightStart < nightEnd} — same-day window, e.g. 01:00–03:00</li>
 *   <li>{@code nightStart > nightEnd} — wraps midnight, e.g. 23:00–07:00</li>
 *   <li>{@code nightStart == nightEnd} — no night slots</li>
 * </ul>
 *
 * A 15-minute slot belongs to night when its {@code startAt.toLocalTime()} is inside the window
 * (inclusive start, exclusive end on the cycle).
 */
public final class NightHours {

    private NightHours() {
    }

    public static boolean isNightSlot(LocalTime slotStart, LocalTime nightStart, LocalTime nightEnd) {
        if (nightStart.equals(nightEnd)) {
            return false;
        }
        if (nightStart.isBefore(nightEnd)) {
            // e.g. 01:00 .. 03:00
            return !slotStart.isBefore(nightStart) && slotStart.isBefore(nightEnd);
        }
        // wraps midnight, e.g. 23:00 .. 07:00 → [23:00, 24:00) ∪ [00:00, 07:00)
        return !slotStart.isBefore(nightStart) || slotStart.isBefore(nightEnd);
    }

    /**
     * All 15-minute aligned starts in {@code [from, to)} that fall into night hours.
     */
    public static List<LocalDateTime> nightSlotsInRange(
            LocalDateTime from,
            LocalDateTime to,
            LocalTime nightStart,
            LocalTime nightEnd
    ) {
        List<LocalDateTime> slots = new ArrayList<>();
        LocalDateTime cursor = from;
        while (cursor.isBefore(to)) {
            if (isNightSlot(cursor.toLocalTime(), nightStart, nightEnd)) {
                slots.add(cursor);
            }
            cursor = cursor.plusMinutes(15);
        }
        return slots;
    }
}
