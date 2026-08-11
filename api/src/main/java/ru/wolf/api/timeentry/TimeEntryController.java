package ru.wolf.api.timeentry;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

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
@RestController
@RequestMapping("/api/v1/time-entries")
@RequiredArgsConstructor
public class TimeEntryController {

    private final TimeEntryRepository timeEntryRepository;
    private final DeloRepository deloRepository;
    private final UserRepository userRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<TimeEntryResponse>> listRange(
            Authentication authentication,
            @RequestParam("from") String from,
            @RequestParam("to") String to
    ) {
        User user = currentUser(authentication);
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

    @GetMapping("/today")
    @Transactional(readOnly = true)
    public ResponseEntity<TodayResponse> today(
            Authentication authentication,
            @RequestParam(value = "date", required = false) String date
    ) {
        User user = currentUser(authentication);
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

        return ResponseEntity.ok(new TodayResponse(
                day.toString(),
                user.getTimezone(),
                formatLdt(bounds.start()),
                formatLdt(bounds.endExclusive()),
                formatTime(user.getDayEnd()),
                entries
        ));
    }

    @GetMapping("/week")
    @Transactional(readOnly = true)
    public ResponseEntity<WeekResponse> week(
            Authentication authentication,
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "isoYear", required = false) Integer isoYear,
            @RequestParam(value = "isoWeek", required = false) Integer isoWeek
    ) {
        User user = currentUser(authentication);
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
    @PutMapping
    @Transactional
    public ResponseEntity<TimeEntryResponse> putEntry(
            Authentication authentication,
            @Valid @RequestBody PutTimeEntryRequest request
    ) {
        User user = currentUser(authentication);
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDateTime start = parseAndValidateSlot(request.getStartAt());
        LocalDateTime end = request.getEndAt() != null && !request.getEndAt().isBlank()
                ? parseAndValidateSlot(request.getEndAt())
                : start.plusMinutes(15);
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("endAt должен быть позже startAt");
        }
        if (java.time.Duration.between(start, end).toMinutes() % 15 != 0) {
            throw new IllegalArgumentException("Длина интервала должна быть кратна 15 минутам");
        }

        Content content = resolveContent(user, request.getDeloId(), request.getAdHocText());
        TimeEntry.Status status = resolveStatus(request.getStatus(), start, zone);

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
    @PostMapping("/grid-click")
    @Transactional
    public ResponseEntity<GridClickResponse> gridClick(
            Authentication authentication,
            @Valid @RequestBody GridClickRequest request
    ) {
        User user = currentUser(authentication);
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDateTime slot = parseAndValidateSlot(request.getSlotStart());
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
        if (request.getDeloId() != null || (request.getAdHocText() != null && !request.getAdHocText().isBlank())) {
            Content content = resolveContent(user, request.getDeloId(), request.getAdHocText());
            TimeEntry.Status status = resolveStatus(request.getStatus(), slot, zone);
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
    @DeleteMapping
    @Transactional
    public ResponseEntity<Void> clearEntry(
            Authentication authentication,
            @RequestParam("startAt") String startAt
    ) {
        User user = currentUser(authentication);
        LocalDateTime start = parseAndValidateSlot(startAt);
        timeEntryRepository.findByUserIdAndStartAt(user.getId(), start)
                .ifPresent(timeEntryRepository::delete);
        return ResponseEntity.noContent().build();
    }

    /** Confirm by interval id or by startAt of the interval. */
    @PostMapping("/confirm")
    @Transactional
    public ResponseEntity<TimeEntryResponse> confirmOne(
            Authentication authentication,
            @Valid @RequestBody ConfirmOneRequest request
    ) {
        User user = currentUser(authentication);
        TimeEntry entry;
        if (request.getId() != null) {
            entry = timeEntryRepository.findById(request.getId())
                    .filter(e -> e.getUser().getId().equals(user.getId()))
                    .orElseThrow(() -> new IllegalArgumentException("Запись времени не найдена"));
        } else if (request.getStartAt() != null && !request.getStartAt().isBlank()) {
            LocalDateTime start = parseAndValidateSlot(request.getStartAt());
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

    @PostMapping("/confirm-all")
    @Transactional
    public ResponseEntity<ConfirmAllResponse> confirmAll(
            Authentication authentication,
            @Valid @RequestBody ConfirmAllRequest request
    ) {
        User user = currentUser(authentication);
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDateTime from = parseLocalDateTime(request.getFrom());
        LocalDateTime to = parseLocalDateTime(request.getTo());
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
    @PostMapping("/ensure-sleep")
    @Transactional
    public ResponseEntity<EnsureSleepResponse> ensureSleep(
            Authentication authentication,
            @Valid @RequestBody EnsureSleepRequest request
    ) {
        User user = currentUser(authentication);
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDateTime from = parseLocalDateTime(request.getFrom());
        LocalDateTime to = parseLocalDateTime(request.getTo());
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

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private TimeEntryResponse toResponse(TimeEntry entry) {
        Delo d = entry.getDelo();
        return new TimeEntryResponse(
                entry.getId(),
                d != null ? d.getId() : null,
                d != null ? d.getTitle() : null,
                entry.getAdHocText(),
                formatLdt(entry.getStartAt()),
                formatLdt(entry.getEndAt()),
                entry.getStatus()
        );
    }

    // --- DTOs ---

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class TimeEntryResponse {
        private Long id;
        private Long deloId;
        private String deloTitle;
        private String adHocText;
        private String startAt;
        private String endAt;
        private TimeEntry.Status status;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class TodayResponse {
        private String date;
        private String timezone;
        private String dayStart;
        private String dayEnd;
        private String dayEndSetting;
        private List<TimeEntryResponse> entries;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class WeekDayInfo {
        private String date;
        private String weekday;
        private String dayStart;
        private String dayEnd;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class WeekResponse {
        private int isoYear;
        private int isoWeek;
        private String weekStart;
        private String weekEndExclusive;
        private String rangeStart;
        private String rangeEnd;
        private String timezone;
        private String dayEndSetting;
        private List<WeekDayInfo> days;
        private List<TimeEntryResponse> entries;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PutTimeEntryRequest {
        @NotBlank
        private String startAt;
        private String endAt;
        private Long deloId;
        @Size(max = 500)
        private String adHocText;
        private TimeEntry.Status status;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class GridClickRequest {
        @NotBlank
        private String slotStart;
        private Long deloId;
        @Size(max = 500)
        private String adHocText;
        private TimeEntry.Status status;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class GridClickResponse {
        /** SHRINK | EXTEND | SPLIT | PLACE | DELETE | NEED_PICKER */
        private String action;
        private List<TimeEntryResponse> entries;
        private String message;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ConfirmOneRequest {
        private Long id;
        private String startAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ConfirmAllRequest {
        @NotBlank private String from;
        @NotBlank private String to;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ConfirmAllResponse {
        private int confirmedCount;
        private List<TimeEntryResponse> entries;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class EnsureSleepRequest {
        @NotBlank private String from;
        @NotBlank private String to;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class EnsureSleepResponse {
        private int filledCount;
        private Long sleepDeloId;
        private List<TimeEntryResponse> entries;
    }
}
