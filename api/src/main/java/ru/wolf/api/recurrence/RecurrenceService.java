package ru.wolf.api.recurrence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecurrenceService {

    static final int DEFAULT_HORIZON_WEEKS = 4;
    static final int MIN_HORIZON_WEEKS = 1;
    static final int MAX_HORIZON_WEEKS = 12;
    static final LocalTime DEFAULT_WINDOW_START = LocalTime.of(9, 0);
    static final LocalTime DEFAULT_WINDOW_END = LocalTime.of(9, 15);

    private final DeloRepository deloRepository;
    private final TimeEntryRepository timeEntryRepository;

    @Transactional
    public ApplyResult apply(User user, Long deloId, ApplyCommand command) {
        Delo delo = deloRepository.findByUserAndId(user, deloId)
                .orElseThrow(() -> new IllegalArgumentException("Дело не найдено"));

        Set<DayOfWeek> weekdays = resolveWeekdays(command.weekdays(), delo);
        if ((command.windowStart() == null) != (command.windowEnd() == null)) {
            throw new IllegalArgumentException("Окно времени должно содержать начало и конец");
        }
        LocalTime windowStart = command.windowStart() != null
                ? command.windowStart()
                : (delo.getRecurrenceWindowStart() != null ? delo.getRecurrenceWindowStart() : DEFAULT_WINDOW_START);
        LocalTime windowEnd = command.windowEnd() != null
                ? command.windowEnd()
                : (delo.getRecurrenceWindowEnd() != null ? delo.getRecurrenceWindowEnd() : DEFAULT_WINDOW_END);
        int horizonWeeks = command.horizonWeeks() != null ? command.horizonWeeks() : DEFAULT_HORIZON_WEEKS;

        validateWindow(windowStart, windowEnd);
        validateHorizon(horizonWeeks);
        if (weekdays.isEmpty()) {
            throw new IllegalArgumentException("Задайте хотя бы один день недели");
        }

        persistRule(delo, weekdays, windowStart, windowEnd);
        deloRepository.save(delo);

        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDateTime now = LocalDateTime.now(zone);
        LocalDate from = now.toLocalDate();
        LocalDate toExclusive = from.plusWeeks(horizonWeeks);

        int created = 0;
        int skippedOccupied = 0;
        int skippedPast = 0;

        for (LocalDate day = from; day.isBefore(toExclusive); day = day.plusDays(1)) {
            if (!weekdays.contains(day.getDayOfWeek())) {
                continue;
            }
            LocalDateTime start = day.atTime(windowStart);
            LocalDateTime end = day.atTime(windowEnd);
            if (start.isBefore(now)) {
                skippedPast++;
                continue;
            }
            if (!timeEntryRepository.findOverlapping(user.getId(), start, end).isEmpty()) {
                skippedOccupied++;
                continue;
            }
            timeEntryRepository.save(TimeEntry.builder()
                    .user(user)
                    .delo(delo)
                    .startAt(start)
                    .endAt(end)
                    .status(TimeEntry.Status.PLANNED)
                    .build());
            created++;
        }

        return new ApplyResult(created, skippedOccupied, skippedPast, horizonWeeks, from, toExclusive);
    }

    public record ApplyCommand(
            List<DayOfWeek> weekdays,
            LocalTime windowStart,
            LocalTime windowEnd,
            Integer horizonWeeks
    ) {}

    public record ApplyResult(
            int created,
            int skippedOccupied,
            int skippedPast,
            int horizonWeeks,
            LocalDate from,
            LocalDate toExclusive
    ) {}

    public static String encodeWeekdays(Collection<DayOfWeek> days) {
        return days.stream()
                .distinct()
                .sorted()
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    public static List<DayOfWeek> decodeWeekdays(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(DayOfWeek::valueOf)
                .distinct()
                .sorted()
                .toList();
    }

    private Set<DayOfWeek> resolveWeekdays(List<DayOfWeek> requested, Delo delo) {
        if (requested != null && !requested.isEmpty()) {
            return EnumSet.copyOf(requested);
        }
        List<DayOfWeek> stored = decodeWeekdays(delo.getRecurrenceWeekdays());
        if (stored.isEmpty()) {
            return EnumSet.noneOf(DayOfWeek.class);
        }
        return EnumSet.copyOf(stored);
    }

    private void persistRule(Delo delo, Set<DayOfWeek> weekdays, LocalTime windowStart, LocalTime windowEnd) {
        delo.setRecurrenceWeekdays(encodeWeekdays(weekdays));
        delo.setRecurrenceWindowStart(windowStart);
        delo.setRecurrenceWindowEnd(windowEnd);
    }

    private void validateHorizon(int horizonWeeks) {
        if (horizonWeeks < MIN_HORIZON_WEEKS || horizonWeeks > MAX_HORIZON_WEEKS) {
            throw new IllegalArgumentException(
                    "Горизонт должен быть от " + MIN_HORIZON_WEEKS + " до " + MAX_HORIZON_WEEKS + " недель");
        }
    }

    private void validateWindow(LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Окно времени должно содержать начало и конец");
        }
        if (start.getSecond() != 0 || start.getNano() != 0 || end.getSecond() != 0 || end.getNano() != 0
                || start.getMinute() % 15 != 0 || end.getMinute() % 15 != 0) {
            throw new IllegalArgumentException("Окно времени должно быть кратно 15 минутам");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Конец окна должен быть позже начала");
        }
        if (Duration.between(start, end).toMinutes() % 15 != 0) {
            throw new IllegalArgumentException("Длина окна должна быть кратна 15 минутам");
        }
    }
}
