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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * API for Записи времени (15-minute grid cells).
 *
 * <p>Semantics:
 * <ul>
 *   <li>Empty cell → no row (status «неопределено»)</li>
 *   <li>Create on future empty → PLANNED (запланирована)</li>
 *   <li>Create on past empty → DONE (выполнена)</li>
 *   <li>At most one entry per (user, start_at)</li>
 *   <li>Ad-hoc text allowed without creating a Дело</li>
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
    public static class PutTimeEntryRequest {
        @NotBlank
        private String startAt;

        private Long deloId;

        @Size(max = 500)
        private String adHocText;

        /** Optional; if null, derived from past/future relative to now in user TZ. */
        private TimeEntry.Status status;
    }
}
