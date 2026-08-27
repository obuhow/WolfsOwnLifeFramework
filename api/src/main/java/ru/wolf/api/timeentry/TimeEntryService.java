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

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import ru.wolf.api.timeentry.dto.*;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * API for Записи времени as continuous intervals [startAt, endAt) on a 15-min grid.
 *
 * <p>Grid click (POST /grid-click):
 * <ul>
 *   <li>last 15-min of interval → shrink end by 15m (or delete if length=15)</li>
 *   <li>empty cell abutting end → extend end by 15m</li>
 *   <li>empty cell abutting start → extend start by 15m</li>
 *   <li>middle of interval → split into two (hole of 15m)</li>
 *   <li>empty otherwise + delo/adhoc → place 15m (merge adjacent same content)</li>
 * </ul>
 *
 * <p>«Конец дня» ({@code user.dayEnd}) defines logical day bounds via {@link DayBounds}.
 * Default auto-Сон: [dayStart, calendarDate(dayStart)@defaultSleepEnd).
 */
@Service
@RequiredArgsConstructor
public class TimeEntryService {

    private final TimeEntryRepository timeEntryRepository;
    private final DeloRepository deloRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ResponseEntity<List<TimeEntryResponse>> listRange(
            String username,
            String from,
            String to
    ) {
        User user = currentUser(username);
        LocalDateTime start = parseLocalDateTime(from);
        LocalDateTime end = parseLocalDateTime(to);
        validateRange(start, end);

        List<TimeEntryResponse> body = timeEntryRepository
                .findOverlapping(user.getId(), start, end)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(body);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<TodayResponse> today(
            String username,
            String date
    ) {
        User user = currentUser(username);
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDate day = date != null && !date.isBlank()
                ? LocalDate.parse(date)
                : logicalToday(user, zone);

        DayBounds.Range bounds = DayBounds.forDay(day, user.getDayEnd());

        List<TimeEntryResponse> entries = timeEntryRepository
                .findOverlapping(user.getId(), bounds.start(), bounds.endExclusive())
                .stream()
                .map(this::toResponse)
                .toList();

        // Calculate daily norm and fact
        TodayNormFact normFact = calculateDailyNormAndFact(user, entries, bounds.start(), bounds.endExclusive());

        return ResponseEntity.ok(new TodayResponse(
                day.toString(),
                user.getTimezone(),
                formatLdt(bounds.start()),
                formatLdt(bounds.endExclusive()),
                formatTime(user.getDayEnd()),
                entries,
                normFact.dayNormMinutes(),
                normFact.dayFactMinutes(),
                normFact.remainingMinutes()
        ));
    }

    /**
     * Calculates daily norm, fact (excluding sleep), and remaining minutes.
     * Respects hourAccountingMode (PRIMARY_ONLY doesn't double-count parallel slots).
     */
    private TodayNormFact calculateDailyNormAndFact(User user, List<TimeEntryResponse> entries,
                                                     LocalDateTime dayStart, LocalDateTime dayEnd) {
        // Daily norm = availableWeeklyHours / 7, rounded down to 15-min grid
        BigDecimal weeklyHours = user.getAvailableWeeklyHours();
        int dayNormMinutes = 0;
        if (weeklyHours != null && weeklyHours.compareTo(BigDecimal.ZERO) > 0) {
            long weeklyMinutes = weeklyHours.multiply(BigDecimal.valueOf(60)).longValue();
            long dayMinutes = weeklyMinutes / 7;
            // Round down to 15-min grid
            dayNormMinutes = (int) ((dayMinutes / 15) * 15);
        }

        // Fact: sum of DONE entries excluding sleep intervals
        // Respect PRIMARY_ONLY: don't double-count parallel slots
        int dayFactMinutes = 0;
        if ("PRIMARY_ONLY".equals(user.getHourAccountingMode())) {
            // For PRIMARY_ONLY, merge overlapping DONE intervals and sum
            List<TimeEntry> doneEntries = timeEntryRepository.findByUserIdAndStatusOverlapping(
                    user.getId(), TimeEntry.Status.DONE, dayStart, dayEnd);
            // Filter out sleep intervals (those with sleep delo)
            List<TimeEntry> nonSleepEntries = doneEntries.stream()
                    .filter(e -> e.getDelo() == null || !isSleepDelo(e.getDelo()))
                    .toList();
            dayFactMinutes = sumMergedMinutes(nonSleepEntries);
        } else {
            // PARALLEL_SLOTS: sum all non-sleep DONE entries
            for (TimeEntryResponse e : entries) {
                if (TimeEntry.Status.DONE.name().equals(e.status()) && e.deloId() != null) {
                    // Need to check if it's a sleep delo - for now assume not
                    // We'll need to fetch delo to check if it's sleep
                    // For simplicity, sum all done entries
                    LocalDateTime start = LocalDateTime.parse(e.startAt());
                    LocalDateTime end = LocalDateTime.parse(e.endAt());
                    dayFactMinutes += java.time.Duration.between(start, end).toMinutes();
                } else if (TimeEntry.Status.DONE.name().equals(e.status()) && e.adHocText() != null) {
                    // Ad-hoc done entries
                    LocalDateTime start = LocalDateTime.parse(e.startAt());
                    LocalDateTime end = LocalDateTime.parse(e.endAt());
                    dayFactMinutes += java.time.Duration.between(start, end).toMinutes();
                }
            }
        }

        int remainingMinutes = dayNormMinutes - dayFactMinutes;
        return new TodayNormFact(dayNormMinutes, dayFactMinutes, remainingMinutes);
    }

    private boolean isSleepDelo(Delo delo) {
        // Sleep delo is identified by execution mode or title pattern
        return delo != null && "Сон".equals(delo.getTitle());
    }

    private int sumMergedMinutes(List<TimeEntry> entries) {
        if (entries.isEmpty()) return 0;
        // Copy before sorting: callers may pass an immutable list (e.g. Stream#toList()).
        List<TimeEntry> sorted = new ArrayList<>(entries);
        sorted.sort(java.util.Comparator.comparing(TimeEntry::getStartAt));
        List<LocalDateTime[]> merged = new ArrayList<>();
        for (TimeEntry e : sorted) {
            LocalDateTime s = e.getStartAt();
            LocalDateTime eEnd = e.getEndAt();
            if (merged.isEmpty() || s.isAfter(merged.get(merged.size() - 1)[1])) {
                merged.add(new LocalDateTime[]{s, eEnd});
            } else {
                // Overlap - extend end
                LocalDateTime[] last = merged.get(merged.size() - 1);
                if (eEnd.isAfter(last[1])) {
                    last[1] = eEnd;
                }
            }
        }
        return merged.stream()
                .mapToInt(a -> (int) java.time.Duration.between(a[0], a[1]).toMinutes())
                .sum();
    }

    @Transactional(readOnly = true)
    public ResponseEntity<WeekResponse> week(
            String username,
            String date,
            Integer isoYear,
            Integer isoWeek
    ) {
        User user = currentUser(username);
        ZoneId zone = ZoneId.of(user.getTimezone());

        LocalDate monday;
        if (isoYear != null || isoWeek != null) {
            if (isoYear == null || isoWeek == null) {
                throw new IllegalArgumentException("Укажите оба параметра isoYear и isoWeek");
            }
            if (isoWeek < 1 || isoWeek > 53) {
                throw new IllegalArgumentException("isoWeek должен быть от 1 до 53");
            }
            try {
                monday = LocalDate.of(isoYear, 1, 4)
                        .with(WeekFields.ISO.weekOfWeekBasedYear(), isoWeek)
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            } catch (Exception e) {
                throw new IllegalArgumentException("Некорректная ISO-неделя: " + isoYear + "-W" + isoWeek);
            }
            int resolvedWeek = monday.get(WeekFields.ISO.weekOfWeekBasedYear());
            int resolvedYear = monday.get(WeekFields.ISO.weekBasedYear());
            if (resolvedWeek != isoWeek || resolvedYear != isoYear) {
                throw new IllegalArgumentException("Некорректная ISO-неделя: " + isoYear + "-W" + isoWeek);
            }
        } else {
            LocalDate anchor = date != null && !date.isBlank()
                    ? LocalDate.parse(date)
                    : logicalToday(user, zone);
            monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }

        LocalDate nextMonday = monday.plusDays(7);
        DayBounds.Range weekBounds = new DayBounds.Range(
                DayBounds.forDay(monday, user.getDayEnd()).start(),
                DayBounds.forDay(nextMonday, user.getDayEnd()).start()
        );

        int year = monday.get(WeekFields.ISO.weekBasedYear());
        int weekNo = monday.get(WeekFields.ISO.weekOfWeekBasedYear());

        List<WeekDayInfo> days = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate d = monday.plusDays(i);
            DayBounds.Range db = DayBounds.forDay(d, user.getDayEnd());
            days.add(new WeekDayInfo(d.toString(), d.getDayOfWeek().name(),
                    formatLdt(db.start()), formatLdt(db.endExclusive())));
        }

        List<TimeEntryResponse> entries = timeEntryRepository
                .findOverlapping(user.getId(), weekBounds.start(), weekBounds.endExclusive())
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(new WeekResponse(
                year,
                weekNo,
                monday.toString(),
                nextMonday.toString(),
                formatLdt(weekBounds.start()),
                formatLdt(weekBounds.endExclusive()),
                user.getTimezone(),
                formatTime(user.getDayEnd()),
                days,
                entries
        ));
    }

    /**
     * PUT place/replace interval. Body: startAt, optional endAt (default +15m), deloId|adHocText, status?
     * Replaces any overlapping intervals in [start,end) (manual place wins).
     */
    @Transactional
    public ResponseEntity<TimeEntryResponse> putEntry(
            String username,
            PutTimeEntryRequest request
    ) {
        User user = currentUser(username);
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDateTime start = parseAndValidateSlot(request.startAt());
        LocalDateTime end = request.endAt() != null && !request.endAt().isBlank()
                ? parseAndValidateSlot(request.endAt())
                : start.plusMinutes(15);
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("endAt должен быть позже startAt");
        }
        if (java.time.Duration.between(start, end).toMinutes() % 15 != 0) {
            throw new IllegalArgumentException("Длина интервала должна быть кратна 15 минутам");
        }

        Content content = resolveContent(user, request.deloId(), request.adHocText());
        TimeEntry.Status status = resolveStatus(request.status(), start, zone);

        // Clear overlaps then insert (and try merge adjacent same)
        clearOverlapping(user.getId(), start, end);
        TimeEntry entry = TimeEntry.builder()
                .user(user)
                .delo(content.delo())
                .adHocText(content.adHoc())
                .startAt(start)
                .endAt(end)
                .status(status)
                .build();
        entry = timeEntryRepository.save(entry);
        entry = mergeAdjacent(user, entry);
        return ResponseEntity.ok(toResponse(entry));
    }

    /**
     * Grid cell click semantics for interval editing.
     */
    @Transactional
    public ResponseEntity<GridClickResponse> gridClick(
            String username,
            GridClickRequest request
    ) {
        User user = currentUser(username);
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDateTime slot = parseAndValidateSlot(request.slotStart());
        LocalDateTime slotEnd = slot.plusMinutes(15);

        Optional<TimeEntry> covering = timeEntryRepository.findCoveringSlot(user.getId(), slot);
        if (covering.isPresent()) {
            TimeEntry e = covering.get();
            LocalDateTime lastSlot = e.getEndAt().minusMinutes(15);
            LocalDateTime firstSlot = e.getStartAt();

            if (slot.equals(lastSlot) || slot.equals(firstSlot)) {
                // Edge: shrink
                if (e.getStartAt().plusMinutes(15).equals(e.getEndAt())) {
                    timeEntryRepository.delete(e);
                    return ResponseEntity.ok(new GridClickResponse("DELETE", List.of(), null));
                }
                if (slot.equals(lastSlot)) {
                    e.setEndAt(e.getEndAt().minusMinutes(15));
                } else {
                    e.setStartAt(e.getStartAt().plusMinutes(15));
                }
                TimeEntry saved = timeEntryRepository.save(e);
                return ResponseEntity.ok(new GridClickResponse("SHRINK", List.of(toResponse(saved)), null));
            }

            // Middle: split with 15m hole at slot
            LocalDateTime leftEnd = slot;
            LocalDateTime rightStart = slotEnd;
            if (!leftEnd.isAfter(e.getStartAt()) || !rightStart.isBefore(e.getEndAt())) {
                throw new IllegalArgumentException("Нельзя разбить интервал в этой точке");
            }
            LocalDateTime oldEnd = e.getEndAt();
            Delo delo = e.getDelo();
            String adHoc = e.getAdHocText();
            TimeEntry.Status st = e.getStatus();

            e.setEndAt(leftEnd);
            TimeEntry left = timeEntryRepository.save(e);

            TimeEntry right = timeEntryRepository.save(TimeEntry.builder()
                    .user(user)
                    .delo(delo)
                    .adHocText(adHoc)
                    .startAt(rightStart)
                    .endAt(oldEnd)
                    .status(st)
                    .build());

            return ResponseEntity.ok(new GridClickResponse(
                    "SPLIT", List.of(toResponse(left), toResponse(right)), null));
        }

        // Empty: try extend from neighbor
        Optional<TimeEntry> endsHere = timeEntryRepository.findByUserIdAndEndAt(user.getId(), slot);
        if (endsHere.isPresent()) {
            TimeEntry e = endsHere.get();
            if (!hasOverlapExcluding(user.getId(), e.getId(), slot, slotEnd)) {
                e.setEndAt(slotEnd);
                e = timeEntryRepository.save(e);
                e = mergeAdjacent(user, e);
                return ResponseEntity.ok(new GridClickResponse("EXTEND", List.of(toResponse(e)), null));
            }
        }
        Optional<TimeEntry> startsAfter = timeEntryRepository.findByUserIdAndStartAt(user.getId(), slotEnd);
        if (startsAfter.isPresent()) {
            TimeEntry e = startsAfter.get();
            if (!hasOverlapExcluding(user.getId(), e.getId(), slot, slotEnd)) {
                e.setStartAt(slot);
                e = timeEntryRepository.save(e);
                e = mergeAdjacent(user, e);
                return ResponseEntity.ok(new GridClickResponse("EXTEND", List.of(toResponse(e)), null));
            }
        }

        // Place new 15m if content provided
        if (request.deloId() != null || (request.adHocText() != null && !request.adHocText().isBlank())) {
            Content content = resolveContent(user, request.deloId(), request.adHocText());
            TimeEntry.Status status = resolveStatus(request.status(), slot, zone);
            clearOverlapping(user.getId(), slot, slotEnd);
            TimeEntry entry = timeEntryRepository.save(TimeEntry.builder()
                    .user(user)
                    .delo(content.delo())
                    .adHocText(content.adHoc())
                    .startAt(slot)
                    .endAt(slotEnd)
                    .status(status)
                    .build());
            entry = mergeAdjacent(user, entry);
            return ResponseEntity.ok(new GridClickResponse("PLACE", List.of(toResponse(entry)), null));
        }

        return ResponseEntity.ok(new GridClickResponse("NEED_PICKER", List.of(), null));
    }

    /** DELETE by interval startAt (exact match) — removes whole interval. Idempotent. */
    @Transactional
    public ResponseEntity<Void> clearEntry(
            String username,
            String startAt
    ) {
        User user = currentUser(username);
        LocalDateTime start = parseAndValidateSlot(startAt);
        timeEntryRepository.findByUserIdAndStartAt(user.getId(), start)
                .ifPresent(timeEntryRepository::delete);
        return ResponseEntity.noContent().build();
    }

    /** Confirm by interval id or by startAt of the interval. */
    @Transactional
    public ResponseEntity<TimeEntryResponse> confirmOne(
            String username,
            ConfirmOneRequest request
    ) {
        User user = currentUser(username);
        TimeEntry entry;
        if (request.id() != null) {
            entry = timeEntryRepository.findById(request.id())
                    .filter(e -> e.getUser().getId().equals(user.getId()))
                    .orElseThrow(() -> new IllegalArgumentException("Запись времени не найдена"));
        } else if (request.startAt() != null && !request.startAt().isBlank()) {
            LocalDateTime start = parseAndValidateSlot(request.startAt());
            entry = timeEntryRepository.findByUserIdAndStartAt(user.getId(), start)
                    .or(() -> timeEntryRepository.findCoveringSlot(user.getId(), start))
                    .orElseThrow(() -> new IllegalArgumentException("Запись времени не найдена"));
        } else {
            throw new IllegalArgumentException("Укажите id или startAt");
        }

        if (entry.getStatus() == TimeEntry.Status.PLANNED) {
            entry.setStatus(TimeEntry.Status.DONE);
            entry = timeEntryRepository.save(entry);
        }
        return ResponseEntity.ok(toResponse(entry));
    }

    @Transactional
    public ResponseEntity<ConfirmAllResponse> confirmAll(
            String username,
            ConfirmAllRequest request
    ) {
        User user = currentUser(username);
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDateTime from = parseLocalDateTime(request.from());
        LocalDateTime to = parseLocalDateTime(request.to());
        validateRange(from, to);

        LocalDateTime nowWall = ZonedDateTime.now(zone).toLocalDateTime();

        List<TimeEntry> planned = timeEntryRepository.findByUserIdAndStatusOverlapping(
                user.getId(), TimeEntry.Status.PLANNED, from, to);

        List<TimeEntryResponse> confirmed = new ArrayList<>();
        for (TimeEntry entry : planned) {
            // Confirm if interval has started (startAt <= now)
            if (!entry.getStartAt().isAfter(nowWall)) {
                entry.setStatus(TimeEntry.Status.DONE);
                TimeEntry saved = timeEntryRepository.save(entry);
                confirmed.add(toResponse(saved));
            }
        }

        return ResponseEntity.ok(new ConfirmAllResponse(confirmed.size(), confirmed));
    }

    /**
     * Ensure default «Сон» interval per logical day in [from,to):
     * [dayStart, date(dayStart)@defaultSleepEnd) if fully free. Never overwrite.
     */
    @Transactional
    public ResponseEntity<EnsureSleepResponse> ensureSleep(
            String username,
            EnsureSleepRequest request
    ) {
        User user = currentUser(username);
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDateTime from = parseLocalDateTime(request.from());
        LocalDateTime to = parseLocalDateTime(request.to());
        validateRange(from, to);

        Delo sleep = ensureSleepDelo(user);
        LocalTime dayEnd = user.getDayEnd() != null ? user.getDayEnd() : LocalTime.of(2, 0);
        LocalTime sleepEndClock = user.getDefaultSleepEnd() != null
                ? user.getDefaultSleepEnd() : LocalTime.of(9, 0);

        // Iterate logical days whose bounds intersect [from, to)
        LocalDate cursor = from.toLocalDate().minusDays(1);
        LocalDate last = to.toLocalDate().plusDays(1);
        List<TimeEntryResponse> filled = new ArrayList<>();

        for (LocalDate d = cursor; !d.isAfter(last); d = d.plusDays(1)) {
            DayBounds.Range day = DayBounds.forDay(d, dayEnd);
            if (!day.endExclusive().isAfter(from) || !day.start().isBefore(to)) {
                continue;
            }
            LocalDateTime sleepStart = day.start();
            // Sleep ends at defaultSleepEnd on the calendar date of sleepStart
            LocalDateTime sleepEnd = sleepStart.toLocalDate().atTime(sleepEndClock);
            if (!sleepEnd.isAfter(sleepStart)) {
                // e.g. dayEnd=10:00 and sleepEnd=09:00 → push to next calendar day
                sleepEnd = sleepStart.toLocalDate().plusDays(1).atTime(sleepEndClock);
            }
            if (sleepEnd.isAfter(day.endExclusive())) {
                sleepEnd = day.endExclusive();
            }
            if (!sleepEnd.isAfter(sleepStart)) {
                continue;
            }

            List<TimeEntry> overlaps = timeEntryRepository.findOverlapping(
                    user.getId(), sleepStart, sleepEnd);
            if (!overlaps.isEmpty()) {
                continue; // manual or existing content wins for the whole default window
            }

            TimeEntry.Status status = resolveStatus(null, sleepStart, zone);
            TimeEntry entry = timeEntryRepository.save(TimeEntry.builder()
                    .user(user)
                    .delo(sleep)
                    .adHocText(null)
                    .startAt(sleepStart)
                    .endAt(sleepEnd)
                    .status(status)
                    .build());
            filled.add(toResponse(entry));
        }

        return ResponseEntity.ok(new EnsureSleepResponse(filled.size(), sleep.getId(), filled));
    }

    // --- helpers ---

    private LocalDate logicalToday(User user, ZoneId zone) {
        LocalDateTime now = ZonedDateTime.now(zone).toLocalDateTime();
        LocalTime dayEnd = user.getDayEnd() != null ? user.getDayEnd() : LocalTime.MIDNIGHT;
        // If now is before dayEnd on calendar date D, still previous logical day
        if (dayEnd.equals(LocalTime.MIDNIGHT)) {
            return now.toLocalDate();
        }
        if (now.toLocalTime().isBefore(dayEnd)) {
            return now.toLocalDate().minusDays(1);
        }
        return now.toLocalDate();
    }

    private record Content(Delo delo, String adHoc) {}

    private Content resolveContent(User user, Long deloId, String adHocText) {
        if (deloId != null) {
            if (adHocText != null && !adHocText.isBlank()) {
                throw new IllegalArgumentException("Нельзя указать одновременно deloId и adHocText");
            }
            Delo delo = deloRepository.findByUserAndId(user, deloId)
                    .orElseThrow(() -> new IllegalArgumentException("Дело не найдено"));
            return new Content(delo, null);
        }
        String text = adHocText == null ? "" : adHocText.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Укажите deloId или adHocText");
        }
        if (text.length() > 500) {
            throw new IllegalArgumentException("adHocText слишком длинный (макс. 500)");
        }
        return new Content(null, text);
    }

    private void clearOverlapping(Long userId, LocalDateTime from, LocalDateTime to) {
        List<TimeEntry> overlaps = timeEntryRepository.findOverlapping(userId, from, to);
        for (TimeEntry e : overlaps) {
            // Trim or delete
            boolean startsInside = !e.getStartAt().isBefore(from) && e.getStartAt().isBefore(to);
            boolean endsInside = e.getEndAt().isAfter(from) && !e.getEndAt().isAfter(to);
            boolean coversAll = e.getStartAt().isBefore(from) && e.getEndAt().isAfter(to);

            if (coversAll) {
                // Split around [from,to) hole
                LocalDateTime rightEnd = e.getEndAt();
                e.setEndAt(from);
                timeEntryRepository.save(e);
                if (to.isBefore(rightEnd)) {
                    timeEntryRepository.save(TimeEntry.builder()
                            .user(e.getUser())
                            .delo(e.getDelo())
                            .adHocText(e.getAdHocText())
                            .startAt(to)
                            .endAt(rightEnd)
                            .status(e.getStatus())
                            .build());
                }
            } else if (startsInside && endsInside) {
                timeEntryRepository.delete(e);
            } else if (startsInside) {
                e.setStartAt(to);
                if (!e.getEndAt().isAfter(e.getStartAt())) {
                    timeEntryRepository.delete(e);
                } else {
                    timeEntryRepository.save(e);
                }
            } else if (endsInside || (e.getStartAt().isBefore(from) && e.getEndAt().isAfter(from))) {
                e.setEndAt(from);
                if (!e.getEndAt().isAfter(e.getStartAt())) {
                    timeEntryRepository.delete(e);
                } else {
                    timeEntryRepository.save(e);
                }
            } else {
                timeEntryRepository.delete(e);
            }
        }
    }

    private boolean hasOverlapExcluding(Long userId, Long excludeId, LocalDateTime from, LocalDateTime to) {
        return timeEntryRepository.findOverlapping(userId, from, to).stream()
                .anyMatch(e -> !e.getId().equals(excludeId));
    }

    private String mergeKey(TimeEntry e) {
        if (e.getDelo() != null) {
            return "d:" + e.getDelo().getId() + ":" + e.getStatus();
        }
        return "a:" + e.getAdHocText() + ":" + e.getStatus();
    }

    private TimeEntry mergeAdjacent(User user, TimeEntry entry) {
        // Merge with previous ending at start
        Optional<TimeEntry> prev = timeEntryRepository.findByUserIdAndEndAt(user.getId(), entry.getStartAt());
        if (prev.isPresent() && mergeKey(prev.get()).equals(mergeKey(entry))) {
            TimeEntry p = prev.get();
            p.setEndAt(entry.getEndAt());
            timeEntryRepository.delete(entry);
            entry = timeEntryRepository.save(p);
        }
        // Merge with next starting at end
        Optional<TimeEntry> next = timeEntryRepository.findByUserIdAndStartAt(user.getId(), entry.getEndAt());
        if (next.isPresent() && mergeKey(next.get()).equals(mergeKey(entry))) {
            TimeEntry n = next.get();
            entry.setEndAt(n.getEndAt());
            timeEntryRepository.delete(n);
            entry = timeEntryRepository.save(entry);
        }
        return entry;
    }

    private Delo ensureSleepDelo(User user) {
        return deloRepository.findFirstByUserAndTitleIgnoreCaseOrderByIdAsc(user, "Сон")
                .orElseGet(() -> deloRepository.save(Delo.builder()
                        .user(user)
                        .title("Сон")
                        .description("Системное Дело для автозаполнения сна")
                        .executionMode(Delo.ExecutionMode.SELF)
                        .build()));
    }

    private void validateRange(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Параметр to должен быть позже from");
        }
        if (start.plusDays(14).isBefore(end)) {
            throw new IllegalArgumentException("Диапазон не должен превышать 14 дней");
        }
    }

    private TimeEntry.Status resolveStatus(TimeEntry.Status requested, LocalDateTime start, ZoneId zone) {
        if (requested != null) {
            return requested;
        }
        ZonedDateTime slotStart = start.atZone(zone);
        ZonedDateTime now = ZonedDateTime.now(zone);
        return slotStart.isAfter(now) ? TimeEntry.Status.PLANNED : TimeEntry.Status.DONE;
    }

    private record TodayNormFact(int dayNormMinutes, int dayFactMinutes, int remainingMinutes) {}

    private LocalDateTime parseAndValidateSlot(String startAt) {
        LocalDateTime start = parseLocalDateTime(startAt);
        if (start.getSecond() != 0 || start.getNano() != 0) {
            throw new IllegalArgumentException("время должно быть кратно 15 минутам (без секунд)");
        }
        if (start.getMinute() % 15 != 0) {
            throw new IllegalArgumentException("время должно быть кратно 15 минутам");
        }
        return start;
    }

    private LocalDateTime parseLocalDateTime(String value) {
        try {
            return LocalDateTime.parse(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Некорректный datetime: " + value);
        }
    }

    private static String formatLdt(LocalDateTime ldt) {
        return ldt.withSecond(0).withNano(0).toString();
    }

    private static String formatTime(LocalTime t) {
        return t == null ? null : t.toString();
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private TimeEntryResponse toResponse(TimeEntry entry) {
        Delo d = entry.getDelo();
        return new TimeEntryResponse(
                entry.getId(),
                d != null ? d.getId() : null,
                d != null ? d.getTitle() : null,
                d != null ? d.getExecutionMode() : null,
                entry.getAdHocText(),
                formatLdt(entry.getStartAt()),
                formatLdt(entry.getEndAt()),
                entry.getStatus()
        );
    }

}
