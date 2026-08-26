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
package ru.wolf.api.calendar;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.project.Project;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.UserRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
public class CalendarController {
    private final UserRepository users;
    private final TimeEntryRepository entries;

    @GetMapping("/month")
    @Transactional(readOnly = true)
    public ResponseEntity<MonthResponse> month(Authentication auth, @RequestParam String month) {
        var user = users.findByUsername(auth.getName()).orElseThrow();
        YearMonth target = YearMonth.parse(month);
        LocalDate gridStart = target.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate gridEnd = target.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).plusDays(1);
        List<TimeEntry> found = entries.findOverlapping(user.getId(), gridStart.atStartOfDay(), gridEnd.atStartOfDay());
        Map<LocalDate, List<EntryResponse>> byDay = new LinkedHashMap<>();
        for (LocalDate date = gridStart; date.isBefore(gridEnd); date = date.plusDays(1)) byDay.put(date, new ArrayList<>());
        for (TimeEntry entry : found) {
            LocalDate date = entry.getStartAt().toLocalDate();
            byDay.computeIfAbsent(date, ignored -> new ArrayList<>()).add(toEntry(entry));
        }
        List<DayResponse> days = byDay.entrySet().stream().map(item -> new DayResponse(item.getKey(), !target.equals(YearMonth.from(item.getKey())), item.getValue(), item.getValue().size())).toList();
        return ResponseEntity.ok(new MonthResponse(target.toString(), gridStart, gridEnd.minusDays(1), days));
    }

    private EntryResponse toEntry(TimeEntry entry) {
        Delo delo = entry.getDelo();
        Project project = null;
        return new EntryResponse(entry.getId(), entry.getStartAt(), delo == null ? null : delo.getId(), delo == null ? entry.getAdHocText() : delo.getTitle(), null, null, entry.getStatus());
    }

    public record MonthResponse(String month, LocalDate gridStart, LocalDate gridEnd, List<DayResponse> days) {}
    public record DayResponse(LocalDate date, boolean outOfMonth, List<EntryResponse> entries, int totalCount) {}
    public record EntryResponse(Long id, LocalDateTime startAt, Long deloId, String deloTitle, Long projectId, String projectTitle, TimeEntry.Status status) {}
}
