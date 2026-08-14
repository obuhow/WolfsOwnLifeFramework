package ru.wolf.api.aggregate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.delo.DeloProject;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.timeentry.DayBounds;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fact-hours aggregates for Project / Delo detail cards.
 *
 * <p>Project attribution mirrors Gantt fact rules:
 * DONE only, ad-hoc excluded, {@code PRIMARY_ONLY} vs {@code ALL_PROJECTS}.
 * Buckets use user {@code dayEnd} via {@link DayBounds}.
 */
@Service
@RequiredArgsConstructor
public class FactAggregateService {

    /** Practical all-time window for personal MVP. */
    private static final LocalDateTime RANGE_FROM = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime RANGE_TO = LocalDateTime.of(2100, 1, 1, 0, 0);

    private final TimeEntryRepository timeEntryRepository;
    private final DeloProjectRepository deloProjectRepository;

    @Transactional(readOnly = true)
    public FactAggregate forProject(User user, Long projectId) {
        String mode = normalizeMode(user.getHourAccountingMode());
        LocalTime dayEnd = effectiveDayEnd(user);

        List<TimeEntry> entries = loadDoneEntries(user.getId());
        Set<Long> deloIds = entries.stream()
                .filter(e -> e.getDelo() != null)
                .map(e -> e.getDelo().getId())
                .collect(Collectors.toSet());

        Map<Long, List<DeloProject>> linksByDelo = loadLinks(deloIds);
        boolean primaryOnly = !"ALL_PROJECTS".equals(mode);

        Map<LocalDate, BigDecimal> byDay = new HashMap<>();

        for (TimeEntry entry : entries) {
            if (entry.getDelo() == null) {
                continue; // ad-hoc never counts toward project fact
            }
            List<DeloProject> links = linksByDelo.getOrDefault(entry.getDelo().getId(), List.of());
            if (!attributesToProject(links, projectId, primaryOnly)) {
                continue;
            }
            accumulateByDay(entry, dayEnd, byDay);
        }

        return toAggregate(byDay, mode);
    }

    @Transactional(readOnly = true)
    public FactAggregate forDelo(User user, Long deloId) {
        String mode = normalizeMode(user.getHourAccountingMode());
        LocalTime dayEnd = effectiveDayEnd(user);

        List<TimeEntry> entries = loadDoneEntries(user.getId());
        Map<LocalDate, BigDecimal> byDay = new HashMap<>();

        for (TimeEntry entry : entries) {
            if (entry.getDelo() == null || !entry.getDelo().getId().equals(deloId)) {
                continue;
            }
            accumulateByDay(entry, dayEnd, byDay);
        }

        return toAggregate(byDay, mode);
    }

    private List<TimeEntry> loadDoneEntries(Long userId) {
        return timeEntryRepository.findByUserIdAndStatusOverlapping(
                userId, TimeEntry.Status.DONE, RANGE_FROM, RANGE_TO);
    }

    private Map<Long, List<DeloProject>> loadLinks(Set<Long> deloIds) {
        Map<Long, List<DeloProject>> linksByDelo = new HashMap<>();
        for (Long deloId : deloIds) {
            linksByDelo.put(deloId, deloProjectRepository.findByDeloId(deloId));
        }
        return linksByDelo;
    }

    /**
     * Same attribution as GanttController.computeFacts for a single project target.
     */
    static boolean attributesToProject(List<DeloProject> links, Long projectId, boolean primaryOnly) {
        if (links == null || links.isEmpty()) {
            return false;
        }
        if (primaryOnly) {
            Long primaryId = links.stream()
                    .filter(l -> Boolean.TRUE.equals(l.getIsPrimary()))
                    .map(l -> l.getProject().getId())
                    .findFirst()
                    .orElse(null);
            return projectId.equals(primaryId);
        }
        return links.stream().anyMatch(l -> projectId.equals(l.getProject().getId()));
    }

    private void accumulateByDay(TimeEntry entry, LocalTime dayEnd, Map<LocalDate, BigDecimal> byDay) {
        LocalDate first = logicalDay(entry.getStartAt(), dayEnd);
        // endAt is exclusive; last covered instant is endAt - 1ns
        LocalDateTime lastInstant = entry.getEndAt().minusNanos(1);
        if (lastInstant.isBefore(entry.getStartAt())) {
            return;
        }
        LocalDate last = logicalDay(lastInstant, dayEnd);

        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            DayBounds.Range bounds = DayBounds.forDay(d, dayEnd);
            double hours = overlapHours(entry.getStartAt(), entry.getEndAt(), bounds.start(), bounds.endExclusive());
            if (hours <= 0) {
                continue;
            }
            BigDecimal h = BigDecimal.valueOf(hours).setScale(2, RoundingMode.HALF_UP);
            byDay.merge(d, h, BigDecimal::add);
        }
    }

    /** Logical calendar day for timestamp under user dayEnd. */
    static LocalDate logicalDay(LocalDateTime t, LocalTime dayEnd) {
        LocalDate candidate = t.toLocalDate();
        DayBounds.Range bounds = DayBounds.forDay(candidate, dayEnd);
        if (t.isBefore(bounds.start())) {
            return candidate.minusDays(1);
        }
        return candidate;
    }

    /** Hours of overlap between [aStart,aEnd) and [bStart,bEnd). */
    static double overlapHours(LocalDateTime aStart, LocalDateTime aEnd,
                               LocalDateTime bStart, LocalDateTime bEnd) {
        LocalDateTime start = aStart.isAfter(bStart) ? aStart : bStart;
        LocalDateTime end = aEnd.isBefore(bEnd) ? aEnd : bEnd;
        if (!end.isAfter(start)) {
            return 0;
        }
        return Duration.between(start, end).toMinutes() / 60.0;
    }

    private FactAggregate toAggregate(Map<LocalDate, BigDecimal> byDay, String mode) {
        List<LocalDate> days = new ArrayList<>(byDay.keySet());
        days.sort(LocalDate::compareTo);

        List<FactAggregate.DayHours> dayHours = new ArrayList<>(days.size());
        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (LocalDate d : days) {
            BigDecimal h = byDay.get(d).setScale(2, RoundingMode.HALF_UP);
            dayHours.add(new FactAggregate.DayHours(d.toString(), h));
            total = total.add(h);
        }
        return new FactAggregate(total.setScale(2, RoundingMode.HALF_UP), mode, dayHours);
    }

    private static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "PRIMARY_ONLY";
        }
        return mode;
    }

    private static LocalTime effectiveDayEnd(User user) {
        return user.getDayEnd() != null ? user.getDayEnd() : LocalTime.of(2, 0);
    }
}
