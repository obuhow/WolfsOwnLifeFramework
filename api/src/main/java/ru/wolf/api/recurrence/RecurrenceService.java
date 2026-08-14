package ru.wolf.api.recurrence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    @Transactional
    public ApplyResult apply(User user, Long deloId, ApplyCommand command) {
        Delo delo = deloRepository.findByUserAndId(user, deloId)
                .orElseThrow(() -> new IllegalArgumentException("Дело не найдено"));

        List<Slot> slots = resolveSlots(command, delo);
        int horizonWeeks = command.horizonWeeks() != null ? command.horizonWeeks() : DEFAULT_HORIZON_WEEKS;
        validateHorizon(horizonWeeks);
        if (slots.isEmpty()) {
            throw new IllegalArgumentException("Задайте хотя бы один слот: день недели и окно времени");
        }
        for (Slot slot : slots) {
            if (slot.weekday() == null) {
                throw new IllegalArgumentException("У слота должен быть день недели");
            }
            validateWindow(slot.windowStart(), slot.windowEnd());
        }

        persistRule(delo, slots);
        deloRepository.save(delo);

        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDateTime now = LocalDateTime.now(zone);
        LocalDate from = now.toLocalDate();
        LocalDate toExclusive = from.plusWeeks(horizonWeeks);

        int created = 0;
        int skippedOccupied = 0;
        int skippedPast = 0;

        for (LocalDate day = from; day.isBefore(toExclusive); day = day.plusDays(1)) {
            for (Slot slot : slots) {
                if (day.getDayOfWeek() != slot.weekday()) {
                    continue;
                }
                LocalDateTime start = day.atTime(slot.windowStart());
                LocalDateTime end = day.atTime(slot.windowEnd());
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
        }

        return new ApplyResult(created, skippedOccupied, skippedPast, horizonWeeks, from, toExclusive);
    }

    public record Slot(DayOfWeek weekday, LocalTime windowStart, LocalTime windowEnd) {}

    public record ApplyCommand(
            List<DayOfWeek> weekdays,
            LocalTime windowStart,
            LocalTime windowEnd,
            Integer horizonWeeks,
            List<Slot> slots
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

    public List<Slot> slotsOf(Delo delo) {
        List<Slot> stored = decodeSlots(delo.getRecurrenceSlots());
        if (!stored.isEmpty()) {
            return stored;
        }
        List<DayOfWeek> weekdays = decodeWeekdays(delo.getRecurrenceWeekdays());
        if (weekdays.isEmpty()) {
            return List.of();
        }
        LocalTime start = delo.getRecurrenceWindowStart() != null ? delo.getRecurrenceWindowStart() : DEFAULT_WINDOW_START;
        LocalTime end = delo.getRecurrenceWindowEnd() != null ? delo.getRecurrenceWindowEnd() : DEFAULT_WINDOW_END;
        return expand(weekdays, start, end);
    }

    private List<Slot> resolveSlots(ApplyCommand command, Delo delo) {
        if (command.slots() != null && !command.slots().isEmpty()) {
            return command.slots();
        }
        if ((command.windowStart() == null) != (command.windowEnd() == null)) {
            throw new IllegalArgumentException("Окно времени должно содержать начало и конец");
        }
        List<DayOfWeek> requestedDays = command.weekdays() != null && !command.weekdays().isEmpty()
                ? command.weekdays()
                : List.of();
        if (!requestedDays.isEmpty()) {
            LocalTime start = command.windowStart() != null
                    ? command.windowStart()
                    : (delo.getRecurrenceWindowStart() != null ? delo.getRecurrenceWindowStart() : DEFAULT_WINDOW_START);
            LocalTime end = command.windowEnd() != null
                    ? command.windowEnd()
                    : (delo.getRecurrenceWindowEnd() != null ? delo.getRecurrenceWindowEnd() : DEFAULT_WINDOW_END);
            return expand(requestedDays, start, end);
        }
        return slotsOf(delo);
    }

    private List<Slot> expand(Collection<DayOfWeek> weekdays, LocalTime start, LocalTime end) {
        return weekdays.stream()
                .distinct()
                .sorted()
                .map(day -> new Slot(day, start, end))
                .toList();
    }

    private void persistRule(Delo delo, List<Slot> slots) {
        Set<DayOfWeek> weekdays = EnumSet.noneOf(DayOfWeek.class);
        slots.forEach(s -> weekdays.add(s.weekday()));
        delo.setRecurrenceWeekdays(encodeWeekdays(weekdays));
        Slot first = slots.get(0);
        delo.setRecurrenceWindowStart(first.windowStart());
        delo.setRecurrenceWindowEnd(first.windowEnd());
        delo.setRecurrenceSlots(encodeSlots(slots));
    }

    private String encodeSlots(List<Slot> slots) {
        try {
            return objectMapper.writeValueAsString(slots);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сохранить правило повторения", e);
        }
    }

    private List<Slot> decodeSlots(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<Slot> parsed = objectMapper.readValue(raw, new TypeReference<List<Slot>>() {});
            return parsed == null ? List.of() : parsed;
        } catch (JsonProcessingException e) {
            return List.of();
        }
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
