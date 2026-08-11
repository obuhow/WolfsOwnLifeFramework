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
