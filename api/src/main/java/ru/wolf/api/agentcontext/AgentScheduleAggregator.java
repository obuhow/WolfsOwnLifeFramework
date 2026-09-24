package ru.wolf.api.agentcontext;

import ru.wolf.api.delo.DeloProject;
import ru.wolf.api.gantt.WeekPlan;
import ru.wolf.api.project.Project;
import ru.wolf.api.routine.Routine;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.user.User;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Pure aggregation layer; it never exposes raw time entries in the resulting context. */
public final class AgentScheduleAggregator {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private AgentScheduleAggregator() {
    }

    public static AgentContext.ScheduleDynamics aggregate(
            User user,
            LocalDate from,
            LocalDate toExclusive,
            List<Project> projects,
            List<WeekPlan> weekPlans,
            List<TimeEntry> entries,
            Map<Long, List<DeloProject>> linksByDelo,
            List<Routine> routines
    ) {
        LocalDate firstMonday = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastMonday = toExclusive.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<LocalDate> weekStarts = new ArrayList<>();
        for (LocalDate monday = firstMonday; monday.isBefore(lastMonday); monday = monday.plusWeeks(1)) {
            weekStarts.add(monday);
        }

        Set<Long> projectIds = projects.stream().map(Project::getId).collect(Collectors.toSet());
        Map<Long, Project> projectById = projects.stream()
                .collect(Collectors.toMap(Project::getId, Function.identity(), (left, ignored) -> left));
        Map<String, Accumulator> weekly = new LinkedHashMap<>();
        for (LocalDate monday : weekStarts) {
            weekly.put(weekId(monday), new Accumulator());
        }

        Map<Long, Accumulator> projectTotals = new HashMap<>();
        Map<String, Accumulator> lifeAreaTotals = new HashMap<>();
        Map<Long, Accumulator> routineTotals = new HashMap<>();
        Map<Long, String> routineLabels = new HashMap<>();

        for (WeekPlan plan : weekPlans) {
            if (!projectIds.contains(plan.getProject().getId())) continue;
            LocalDate monday = mondayOf(plan.getIsoYear(), plan.getIsoWeek());
            Accumulator week = weekly.get(weekId(monday));
            if (week == null) continue;
            BigDecimal hours = scale(plan.getPlanHours());
            week.planned = week.planned.add(hours);
            projectTotals.computeIfAbsent(plan.getProject().getId(), ignored -> new Accumulator()).planned =
                    projectTotals.get(plan.getProject().getId()).planned.add(hours);
            String area = plan.getProject().getLifeArea().getName();
            lifeAreaTotals.computeIfAbsent(area, ignored -> new Accumulator()).planned =
                    lifeAreaTotals.get(area).planned.add(hours);
        }

        boolean primaryOnly = !"ALL_PROJECTS".equalsIgnoreCase(user.getHourAccountingMode());
        for (TimeEntry entry : entries) {
            if (entry.getStartAt() == null || entry.getEndAt() == null
                    || !entry.getEndAt().isAfter(entry.getStartAt())) continue;
            if (entry.getEndAt().isBefore(from.atStartOfDay())
                    || !entry.getStartAt().isBefore(toExclusive.atStartOfDay())) continue;
            boolean fact = entry.getStatus() == TimeEntry.Status.DONE;
            boolean planned = entry.getStatus() == TimeEntry.Status.PLANNED;
            if (!fact && !planned) continue;

            LocalDateTime boundedStart = entry.getStartAt().isAfter(from.atStartOfDay())
                    ? entry.getStartAt() : from.atStartOfDay();
            LocalDateTime boundedEnd = entry.getEndAt().isBefore(toExclusive.atStartOfDay())
                    ? entry.getEndAt() : toExclusive.atStartOfDay();
            if (entry.getDelo() != null && entry.getDelo().isSupporting()) {
                long routineId = entry.getDelo().getId();
                routineLabels.put(routineId, entry.getDelo().getTitle());
                Accumulator total = routineTotals.computeIfAbsent(routineId, ignored -> new Accumulator());
                add(total, fact, planned, hoursBetween(boundedStart, boundedEnd));
                addByWeek(weekly, boundedStart, boundedEnd, fact, planned, true);
                continue;
            }
            if (entry.getDelo() == null) continue;

            List<DeloProject> links = linksByDelo.getOrDefault(entry.getDelo().getId(), List.of());
            List<Long> targets;
            if (primaryOnly) {
                Long primaryId = links.stream()
                        .filter(link -> Boolean.TRUE.equals(link.getIsPrimary()))
                        .map(link -> link.getProject().getId())
                        .findFirst().orElse(null);
                if (primaryId == null) continue;
                targets = List.of(primaryId);
            } else {
                targets = links.stream().map(link -> link.getProject().getId()).distinct().toList();
            }
            for (Long projectId : targets) {
                Project project = projectById.get(projectId);
                if (project == null) continue;
                long minutes = hoursBetween(boundedStart, boundedEnd);
                add(projectTotals.computeIfAbsent(projectId, ignored -> new Accumulator()), fact, planned, minutes);
                add(lifeAreaTotals.computeIfAbsent(project.getLifeArea().getName(), ignored -> new Accumulator()),
                        fact, planned, minutes);
                addByWeek(weekly, boundedStart, boundedEnd, fact, planned, false);
            }
        }

        // A separate Routine has a weekly budget but no Delo link in the domain model.
        // Preserve its plan as a bounded aggregate; facts remain absent rather than guessed.
        for (Routine routine : routines) {
            Accumulator total = routineTotals.computeIfAbsent(-routine.getId(), ignored -> new Accumulator());
            total.planned = total.planned.add(scale(routine.getWeeklyHours()).multiply(BigDecimal.valueOf(weekStarts.size())));
            for (LocalDate monday : weekStarts) {
                weekly.get(weekId(monday)).routinePlanned = weekly.get(weekId(monday)).routinePlanned.add(scale(routine.getWeeklyHours()));
            }
        }

        boolean available = weekly.values().stream().anyMatch(Accumulator::hasAny) || !routines.isEmpty();
        List<AgentContext.WeekDynamics> weeks = available
                ? weekly.entrySet().stream().filter(entry -> entry.getValue().hasAny())
                .map(entry -> entry.getValue().toWeek(entry.getKey())).toList()
                : List.of();
        String note = available ? null : "За выбранный период нет данных расписания: история ещё не накоплена.";
        return new AgentContext.ScheduleDynamics(
                available,
                note,
                weeks,
                toHours(projectTotals, projectById, Project::getTitle),
                toLifeAreaHours(lifeAreaTotals),
                toRoutineHours(routineTotals, routineLabels, routines),
                trend(weeks));
    }

    private static void addByWeek(Map<String, Accumulator> weekly, LocalDateTime start, LocalDateTime end,
                                  boolean fact, boolean planned, boolean routine) {
        for (Map.Entry<String, Accumulator> item : weekly.entrySet()) {
            LocalDate monday = parseWeek(item.getKey());
            LocalDateTime weekStart = monday.atStartOfDay();
            LocalDateTime weekEnd = monday.plusWeeks(1).atStartOfDay();
            long minutes = hoursBetween(max(start, weekStart), min(end, weekEnd));
            if (minutes == 0) continue;
            if (routine) {
                add(item.getValue(), fact, planned, minutes, true);
            } else {
                add(item.getValue(), fact, planned, minutes, false);
            }
        }
    }

    private static void add(Accumulator target, boolean fact, boolean planned, long minutes) {
        add(target, fact, planned, minutes, false);
    }

    private static void add(Accumulator target, boolean fact, boolean planned, long minutes, boolean routine) {
        BigDecimal hours = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        if (fact) {
            if (routine) target.routineFact = target.routineFact.add(hours); else target.fact = target.fact.add(hours);
        }
        if (planned) {
            if (routine) target.routinePending = target.routinePending.add(hours); else target.pending = target.pending.add(hours);
        }
    }

    private static long hoursBetween(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) return 0;
        return Duration.between(start, end).toMinutes();
    }

    private static LocalDateTime max(LocalDateTime left, LocalDateTime right) { return left.isAfter(right) ? left : right; }
    private static LocalDateTime min(LocalDateTime left, LocalDateTime right) { return left.isBefore(right) ? left : right; }

    private static List<AgentContext.Hours> toHours(Map<Long, Accumulator> totals, Map<Long, Project> projects,
                                                    Function<Project, String> label) {
        return totals.entrySet().stream()
                .filter(entry -> projects.containsKey(entry.getKey()) && entry.getValue().hasTotals())
                .map(entry -> new AgentContext.Hours(entry.getKey(), label.apply(projects.get(entry.getKey())),
                        nullable(entry.getValue().planned), nullable(entry.getValue().fact),
                        nullable(entry.getValue().pending)))
                .sorted(Comparator.comparing(AgentContext.Hours::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static List<AgentContext.Hours> toLifeAreaHours(Map<String, Accumulator> totals) {
        return totals.entrySet().stream().filter(entry -> entry.getValue().hasTotals())
                .map(entry -> new AgentContext.Hours(null, entry.getKey(), nullable(entry.getValue().planned),
                        nullable(entry.getValue().fact), nullable(entry.getValue().pending)))
                .sorted(Comparator.comparing(AgentContext.Hours::label, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static List<AgentContext.Hours> toRoutineHours(Map<Long, Accumulator> totals,
                                                           Map<Long, String> supportingLabels,
                                                           List<Routine> routines) {
        Map<Long, String> labels = new HashMap<>(supportingLabels);
        routines.forEach(routine -> labels.put(-routine.getId(), routine.getTitle()));
        return totals.entrySet().stream().filter(entry -> entry.getValue().hasTotals())
                .map(entry -> new AgentContext.Hours(entry.getKey(), labels.get(entry.getKey()),
                        nullable(entry.getValue().planned), nullable(entry.getValue().fact),
                        nullable(entry.getValue().pending)))
                .sorted(Comparator.comparing(AgentContext.Hours::label, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static AgentContext.Trend trend(List<AgentContext.WeekDynamics> weeks) {
        if (weeks.isEmpty()) return new AgentContext.Trend("UNAVAILABLE", null, null, null);
        int split = Math.max(1, weeks.size() / 2);
        BigDecimal first = sumFacts(weeks.subList(0, split));
        BigDecimal second = sumFacts(weeks.subList(split, weeks.size()));
        BigDecimal change = second.subtract(first).setScale(2, RoundingMode.HALF_UP);
        String direction = change.signum() > 0 ? "UP" : change.signum() < 0 ? "DOWN" : "STABLE";
        return new AgentContext.Trend(direction, change, first, second);
    }

    private static BigDecimal sumFacts(List<AgentContext.WeekDynamics> weeks) {
        return weeks.stream().map(AgentContext.WeekDynamics::factHours)
                .filter(value -> value != null).reduce(ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nullable(BigDecimal value) { return value.signum() == 0 ? null : scale(value); }
    private static BigDecimal scale(BigDecimal value) { return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP); }

    private static String weekId(LocalDate monday) {
        WeekFields iso = WeekFields.ISO;
        return "%04d-W%02d".formatted(monday.get(iso.weekBasedYear()), monday.get(iso.weekOfWeekBasedYear()));
    }

    private static LocalDate mondayOf(int year, int week) {
        return LocalDate.of(year, 1, 4).with(WeekFields.ISO.weekOfWeekBasedYear(), week)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static LocalDate parseWeek(String value) {
        return mondayOf(Integer.parseInt(value.substring(0, 4)), Integer.parseInt(value.substring(6)));
    }

    private static final class Accumulator {
        private BigDecimal planned = ZERO;
        private BigDecimal fact = ZERO;
        private BigDecimal pending = ZERO;
        private BigDecimal routinePlanned = ZERO;
        private BigDecimal routineFact = ZERO;
        private BigDecimal routinePending = ZERO;

        private boolean hasAny() {
            return planned.signum() != 0 || fact.signum() != 0 || pending.signum() != 0
                    || routinePlanned.signum() != 0 || routineFact.signum() != 0 || routinePending.signum() != 0;
        }
        private boolean hasTotals() { return planned.signum() != 0 || fact.signum() != 0 || pending.signum() != 0; }
        private AgentContext.WeekDynamics toWeek(String weekId) {
            return new AgentContext.WeekDynamics(weekId, nullable(planned), nullable(fact), nullable(pending),
                    nullable(routinePlanned), nullable(routineFact), nullable(routinePending));
        }
    }
}
