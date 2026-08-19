package ru.wolf.api.goal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.delo.DeloProject;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.WeekFields;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GoalFactService {

    private static final DateTimeFormatter ISO_WEEK = DateTimeFormatter.ofPattern("YYYY-'W'ww-e");

    private final TimeEntryRepository timeEntryRepository;
    private final DeloProjectRepository deloProjectRepository;
    private final GoalProjectRepository goalProjectRepository;

    @Transactional(readOnly = true)
    public BigDecimal calculate(User user, Goal goal, String week) {
        IsoWeek isoWeek = parseWeek(week);
        LocalDateTime from = isoWeek.start().atStartOfDay();
        LocalDateTime to = isoWeek.start().plusWeeks(1).atStartOfDay();
        Set<Long> goalProjectIds = new HashSet<>(goalProjectRepository.findLinks(goal.getId()).stream()
                .map(link -> link.getProject().getId())
                .toList());
        if (goalProjectIds.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        boolean allProjects = "ALL_PROJECTS".equalsIgnoreCase(user.getHourAccountingMode());
        BigDecimal total = BigDecimal.ZERO;
        List<TimeEntry> entries = timeEntryRepository.findByUserIdAndStatusOverlapping(
                user.getId(), TimeEntry.Status.DONE, from, to);
        for (TimeEntry entry : entries) {
            if (entry.getDelo() == null) {
                continue;
            }
            List<DeloProject> links = deloProjectRepository.findByDeloId(entry.getDelo().getId());
            boolean belongs = allProjects
                    ? links.stream().anyMatch(link -> goalProjectIds.contains(link.getProject().getId()))
                    : links.stream().anyMatch(link -> Boolean.TRUE.equals(link.getIsPrimary())
                            && goalProjectIds.contains(link.getProject().getId()));
            if (!belongs) {
                continue;
            }
            LocalDateTime start = entry.getStartAt().isAfter(from) ? entry.getStartAt() : from;
            LocalDateTime end = entry.getEndAt().isBefore(to) ? entry.getEndAt() : to;
            if (end.isAfter(start)) {
                total = total.add(BigDecimal.valueOf(Duration.between(start, end).toMinutes())
                        .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP));
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public IsoWeek parseWeek(String value) {
        if (value == null || !value.matches("\\d{4}-W\\d{2}")) {
            throw new IllegalArgumentException("Неделя должна быть в формате YYYY-Www");
        }
        try {
            int year = Integer.parseInt(value.substring(0, 4));
            int week = Integer.parseInt(value.substring(6, 8));
            if (week < 1 || week > 53) {
                throw new IllegalArgumentException("Недопустимый номер ISO-недели");
            }
            LocalDate start = LocalDate.of(year, 1, 4)
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .plusWeeks(week - 1L);
            if (start.get(WeekFields.ISO.weekBasedYear()) != year
                    || start.get(WeekFields.ISO.weekOfWeekBasedYear()) != week) {
                throw new IllegalArgumentException("Недопустимая ISO-неделя: " + value);
            }
            return new IsoWeek(year, week, start);
        } catch (DateTimeParseException | ArithmeticException ex) {
            throw new IllegalArgumentException("Недопустимая ISO-неделя: " + value);
        }
    }

    public record IsoWeek(int year, int week, LocalDate start) {
        public String value() {
            return "%04d-W%02d".formatted(year, week);
        }
    }
}
