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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * API for Записи времени (15-minute grid cells).
 *
 * <p>Semantics:
 * <ul>
 *   <li>Empty cell → no row (status «неопределено»)</li>
 *   <li>Create on future empty → PLANNED (запланирована)</li>
 *   <li>Create on past empty → DONE (выполнена)</li>
 *   <li>Past PLANNED stays PLANNED until explicit confirm (single or confirm-all)</li>
 *   <li>At most one entry per (user, start_at)</li>
 *   <li>Ad-hoc text allowed without creating a Дело</li>
 *   <li>Ночные часы: POST ensure-sleep fills empty night slots with Дело «Сон»; manual entry wins</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/time-entries")
@RequiredArgsConstructor
public class TimeEntryController {

    private final TimeEntryRepository timeEntryRepository;
    private final DeloRepository deloRepository;
    private final UserRepository userRepository;

    /**
     * GET /api/v1/time-entries?from=&to=
     * Range in user local wall-clock (ISO local datetime). Exclusive end.
     */
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
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Параметр to должен быть позже from");
        }
        if (start.plusDays(14).isBefore(end)) {
            throw new IllegalArgumentException("Диапазон не должен превышать 14 дней");
        }

        List<TimeEntryResponse> body = timeEntryRepository
                .findByUserIdAndStartAtBetween(user.getId(), start, end)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(body);
    }

    /**
     * GET /api/v1/time-entries/today?date=YYYY-MM-DD (optional, default = today in user TZ)
     * Returns day bounds + entries for the side panel «на сегодня».
     */
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
                : ZonedDateTime.now(zone).toLocalDate();

        LocalDateTime dayStart = day.atStartOfDay();
        LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();

        List<TimeEntryResponse> entries = timeEntryRepository
                .findByUserIdAndStartAtBetween(user.getId(), dayStart, dayEnd)
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(new TodayResponse(
                day.toString(),
                user.getTimezone(),
                formatLdt(dayStart),
                formatLdt(dayEnd),
                entries
        ));
    }

    /**
     * GET /api/v1/time-entries/week
     * ISO week Mon–Sun in user timezone wall-clock.
     * Query (mutually preferred in order):
     * <ul>
     *   <li>{@code isoYear}+{@code isoWeek} — pick week by ISO week-year numbers</li>
     *   <li>{@code date=YYYY-MM-DD} — week containing that local date</li>
     *   <li>default — week of «today» in user TZ</li>
     * </ul>
     * Range is half-open: [Monday 00:00, next Monday 00:00).
     */
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
            // Reject week numbers that don't exist for that ISO year (e.g. 2026-W54)
            int resolvedWeek = monday.get(WeekFields.ISO.weekOfWeekBasedYear());
            int resolvedYear = monday.get(WeekFields.ISO.weekBasedYear());
            if (resolvedWeek != isoWeek || resolvedYear != isoYear) {
                throw new IllegalArgumentException("Некорректная ISO-неделя: " + isoYear + "-W" + isoWeek);
            }
        } else {
            LocalDate anchor = date != null && !date.isBlank()
                    ? LocalDate.parse(date)
                    : ZonedDateTime.now(zone).toLocalDate();
            monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }

        LocalDate nextMonday = monday.plusDays(7);
        LocalDateTime rangeStart = monday.atStartOfDay();
        LocalDateTime rangeEnd = nextMonday.atStartOfDay();

        int year = monday.get(WeekFields.ISO.weekBasedYear());
        int weekNo = monday.get(WeekFields.ISO.weekOfWeekBasedYear());

        List<WeekDayInfo> days = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate d = monday.plusDays(i);
            days.add(new WeekDayInfo(d.toString(), d.getDayOfWeek().name()));
        }

        List<TimeEntryResponse> entries = timeEntryRepository
                .findByUserIdAndStartAtBetween(user.getId(), rangeStart, rangeEnd)
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(new WeekResponse(
                year,
                weekNo,
                monday.toString(),
                nextMonday.toString(),
                formatLdt(rangeStart),
                formatLdt(rangeEnd),
                user.getTimezone(),
                days,
                entries
        ));
    }

    /**
     * PUT /api/v1/time-entries
     * Place or replace a Запись времени in the cell.
     * Status auto: past → DONE, future → PLANNED (unless explicit status provided).
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
        LocalDateTime end = start.plusMinutes(15);

        Delo delo = null;
        String adHoc = null;
        if (request.getDeloId() != null) {
            if (request.getAdHocText() != null && !request.getAdHocText().isBlank()) {
                throw new IllegalArgumentException("Нельзя указать одновременно deloId и adHocText");
            }
            delo = deloRepository.findByUserAndId(user, request.getDeloId())
                    .orElseThrow(() -> new IllegalArgumentException("Дело не найдено"));
        } else {
            String text = request.getAdHocText() == null ? "" : request.getAdHocText().trim();
            if (text.isEmpty()) {
                throw new IllegalArgumentException("Укажите deloId или adHocText");
            }
            if (text.length() > 500) {
                throw new IllegalArgumentException("adHocText слишком длинный (макс. 500)");
            }
            adHoc = text;
        }

        TimeEntry.Status status = resolveStatus(request.getStatus(), start, zone);

        Optional<TimeEntry> existing = timeEntryRepository.findByUserIdAndStartAt(user.getId(), start);
        TimeEntry entry;
        if (existing.isPresent()) {
            entry = existing.get();
            entry.setDelo(delo);
            entry.setAdHocText(adHoc);
            entry.setStatus(status);
            entry.setEndAt(end);
        } else {
            entry = TimeEntry.builder()
                    .user(user)
                    .delo(delo)
                    .adHocText(adHoc)
                    .startAt(start)
                    .endAt(end)
                    .status(status)
                    .build();
        }

        TimeEntry saved = timeEntryRepository.save(entry);
        return ResponseEntity.ok(toResponse(saved));
    }

    /**
     * DELETE /api/v1/time-entries?startAt=
     * Clear the cell (toggle-off / снять запись). Idempotent.
     */
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

    /**
     * POST /api/v1/time-entries/confirm
     * Manual confirm of one cell: PLANNED → DONE. Already DONE is idempotent.
     * System never auto-flips past planned without this (or confirm-all).
     */
    @PostMapping("/confirm")
    @Transactional
    public ResponseEntity<TimeEntryResponse> confirmOne(
            Authentication authentication,
            @Valid @RequestBody ConfirmOneRequest request
    ) {
        User user = currentUser(authentication);
        LocalDateTime start = parseAndValidateSlot(request.getStartAt());
        TimeEntry entry = timeEntryRepository.findByUserIdAndStartAt(user.getId(), start)
                .orElseThrow(() -> new IllegalArgumentException("Запись времени не найдена"));

        if (entry.getStatus() == TimeEntry.Status.PLANNED) {
            entry.setStatus(TimeEntry.Status.DONE);
            entry = timeEntryRepository.save(entry);
        }
        return ResponseEntity.ok(toResponse(entry));
    }

    /**
     * POST /api/v1/time-entries/confirm-all
     * Confirm all PLANNED entries in [from, to) whose slot start is not after now
     * in the user timezone — accepts a block of past plan as fact.
     * Future planned in the same range is left untouched.
     */
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
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("Параметр to должен быть позже from");
        }
        if (from.plusDays(14).isBefore(to)) {
            throw new IllegalArgumentException("Диапазон не должен превышать 14 дней");
        }

        LocalDateTime nowWall = ZonedDateTime.now(zone).toLocalDateTime();

        List<TimeEntry> planned = timeEntryRepository.findByUserIdAndStatusAndStartAtBetween(
                user.getId(), TimeEntry.Status.PLANNED, from, to);

        List<TimeEntryResponse> confirmed = new java.util.ArrayList<>();
        for (TimeEntry entry : planned) {
            // Only past/current slots: startAt <= now in user wall-clock.
            if (!entry.getStartAt().isAfter(nowWall)) {
                entry.setStatus(TimeEntry.Status.DONE);
                TimeEntry saved = timeEntryRepository.save(entry);
                confirmed.add(toResponse(saved));
            }
        }

        return ResponseEntity.ok(new ConfirmAllResponse(confirmed.size(), confirmed));
    }

    /**
     * POST /api/v1/time-entries/ensure-sleep
     * For each empty night-hour slot in [from, to) place Дело «Сон» (seed/ensure per user).
     * Existing entries are never overwritten (manual override wins). Idempotent.
     *
     * <p>Called by UI on day/week load. Range rules match list/confirm-all (to &gt; from, max 14d).
     * Slot status: past → DONE, future → PLANNED (same as PUT without explicit status).
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
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("Параметр to должен быть позже from");
        }
        if (from.plusDays(14).isBefore(to)) {
            throw new IllegalArgumentException("Диапазон не должен превышать 14 дней");
        }

        // Align range starts to 15-min grid so we never invent unaligned slots
        from = alignUpToSlot(from);
        to = alignDownExclusiveEnd(to);
        if (!to.isAfter(from)) {
            Delo sleep = ensureSleepDelo(user);
            return ResponseEntity.ok(new EnsureSleepResponse(0, sleep.getId(), List.of()));
        }

        Delo sleep = ensureSleepDelo(user);
        LocalTime nightStart = user.getNightStart();
        LocalTime nightEnd = user.getNightEnd();

        List<LocalDateTime> nightSlots = NightHours.nightSlotsInRange(from, to, nightStart, nightEnd);
        if (nightSlots.isEmpty()) {
            return ResponseEntity.ok(new EnsureSleepResponse(0, sleep.getId(), List.of()));
        }

        List<TimeEntry> existing = timeEntryRepository.findByUserIdAndStartAtBetween(
                user.getId(), from, to);
        Set<LocalDateTime> occupied = new HashSet<>();
        for (TimeEntry e : existing) {
            occupied.add(e.getStartAt());
        }

        List<TimeEntryResponse> filled = new ArrayList<>();
        for (LocalDateTime slot : nightSlots) {
            if (occupied.contains(slot)) {
                continue;
            }
            TimeEntry.Status status = resolveStatus(null, slot, zone);
            TimeEntry entry = TimeEntry.builder()
                    .user(user)
                    .delo(sleep)
                    .adHocText(null)
                    .startAt(slot)
                    .endAt(slot.plusMinutes(15))
                    .status(status)
                    .build();
            TimeEntry saved = timeEntryRepository.save(entry);
            filled.add(toResponse(saved));
        }

        return ResponseEntity.ok(new EnsureSleepResponse(filled.size(), sleep.getId(), filled));
    }

    /** Ensure per-user system Дело «Сон» (no project links). Reuses existing by title. */
    private Delo ensureSleepDelo(User user) {
        return deloRepository.findFirstByUserAndTitleIgnoreCaseOrderByIdAsc(user, "Сон")
                .orElseGet(() -> deloRepository.save(Delo.builder()
                        .user(user)
                        .title("Сон")
                        .description("Системное Дело для автозаполнения ночных часов")
                        .executionMode(Delo.ExecutionMode.SELF)
                        .build()));
    }

    /** Snap forward to next 15-min boundary if not already aligned. */
    private static LocalDateTime alignUpToSlot(LocalDateTime value) {
        LocalDateTime t = value.withSecond(0).withNano(0);
        if (value.getSecond() == 0 && value.getNano() == 0 && t.getMinute() % 15 == 0) {
            return t;
        }
        int rem = t.getMinute() % 15;
        if (rem != 0) {
            t = t.plusMinutes(15 - rem);
        } else {
            // aligned minute but had sub-minute junk → next slot
            t = t.plusMinutes(15);
        }
        return t.withSecond(0).withNano(0);
    }

    /** Snap exclusive end down to a 15-min boundary. */
    private static LocalDateTime alignDownExclusiveEnd(LocalDateTime value) {
        LocalDateTime t = value.withSecond(0).withNano(0);
        int rem = t.getMinute() % 15;
        if (rem != 0) {
            t = t.minusMinutes(rem);
        }
        return t;
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
            throw new IllegalArgumentException("startAt должен быть кратен 15 минутам (без секунд)");
        }
        if (start.getMinute() % 15 != 0) {
            throw new IllegalArgumentException("startAt должен быть кратен 15 минутам");
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

    /** Always emit seconds so clients can match slots stably. */
    private static String formatLdt(LocalDateTime ldt) {
        return ldt.withSecond(0).withNano(0).toString();
        // LocalDateTime.toString() omits seconds when zero → "2026-08-11T10:00"
        // Frontend normalizes both forms.
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeEntryResponse {
        private Long id;
        private Long deloId;
        private String deloTitle;
        private String adHocText;
        private String startAt;
        private String endAt;
        private TimeEntry.Status status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TodayResponse {
        private String date;
        private String timezone;
        private String dayStart;
        private String dayEnd;
        private List<TimeEntryResponse> entries;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeekDayInfo {
        private String date;
        /** java.time.DayOfWeek name, e.g. MONDAY */
        private String weekday;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeekResponse {
        private int isoYear;
        private int isoWeek;
        /** Monday date YYYY-MM-DD */
        private String weekStart;
        /** Next Monday date YYYY-MM-DD (exclusive end of week) */
        private String weekEndExclusive;
        private String rangeStart;
        private String rangeEnd;
        private String timezone;
        private List<WeekDayInfo> days;
        private List<TimeEntryResponse> entries;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PutTimeEntryRequest {
        @NotBlank
        private String startAt;

        private Long deloId;

        @Size(max = 500)
        private String adHocText;

        /** Optional; if null, derived from past/future relative to now in user TZ. */
        private TimeEntry.Status status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfirmOneRequest {
        @NotBlank
        private String startAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfirmAllRequest {
        @NotBlank
        private String from;

        @NotBlank
        private String to;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfirmAllResponse {
        private int confirmedCount;
        private List<TimeEntryResponse> entries;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnsureSleepRequest {
        @NotBlank
        private String from;

        @NotBlank
        private String to;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnsureSleepResponse {
        /** How many empty night cells were filled in this call (0 if already ensured). */
        private int filledCount;
        private Long sleepDeloId;
        /** Only newly created sleep entries (not the full day). */
        private List<TimeEntryResponse> entries;
    }
}
