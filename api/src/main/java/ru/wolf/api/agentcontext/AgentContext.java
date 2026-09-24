package ru.wolf.api.agentcontext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Deterministic, bounded context prepared for the planning agent. */
public record AgentContext(
        Instant generatedAt,
        Period period,
        List<ProjectSummary> projects,
        List<GoalSummary> goals,
        List<RoutineSummary> routines,
        ScheduleDynamics dynamics,
        String prompt
) {
    public AgentContext {
        projects = List.copyOf(projects);
        goals = List.copyOf(goals);
        routines = List.copyOf(routines);
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Контекст агента должен содержать prompt");
        }
    }

    public record Period(LocalDate from, LocalDate toExclusive, int weeks) {
    }

    public record ProjectSummary(
            Long id,
            String title,
            String lifeArea,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal totalPlanHours
    ) {
    }

    public record GoalSummary(
            Long id,
            String title,
            String description,
            int priority,
            List<String> projectTitles
    ) {
        public GoalSummary {
            projectTitles = List.copyOf(projectTitles);
        }
    }

    public record RoutineSummary(
            Long id,
            String title,
            String description,
            BigDecimal weeklyHours,
            List<String> schedule
    ) {
        public RoutineSummary {
            schedule = List.copyOf(schedule);
        }
    }

    public record ScheduleDynamics(
            boolean historyAvailable,
            String historyNote,
            List<WeekDynamics> weeks,
            List<Hours> projectHours,
            List<Hours> lifeAreaHours,
            List<Hours> routineHours,
            Trend trend
    ) {
        public ScheduleDynamics {
            weeks = List.copyOf(weeks);
            projectHours = List.copyOf(projectHours);
            lifeAreaHours = List.copyOf(lifeAreaHours);
            routineHours = List.copyOf(routineHours);
        }
    }

    public record WeekDynamics(
            String weekId,
            BigDecimal plannedHours,
            BigDecimal factHours,
            BigDecimal pendingHours,
            BigDecimal routinePlannedHours,
            BigDecimal routineFactHours,
            BigDecimal routinePendingHours
    ) {
        public WeekDynamics(String weekId, BigDecimal plannedHours, BigDecimal factHours,
                            BigDecimal routinePlannedHours, BigDecimal routineFactHours) {
            this(weekId, plannedHours, factHours, null, routinePlannedHours, routineFactHours, null);
        }
    }

    public record Hours(Long id, String label, BigDecimal planned, BigDecimal fact, BigDecimal pending) {
        public Hours(Long id, String label, BigDecimal planned, BigDecimal fact) {
            this(id, label, planned, fact, null);
        }
    }

    public record Trend(
            String direction,
            BigDecimal changeHours,
            BigDecimal firstHalfFact,
            BigDecimal secondHalfFact
    ) {
    }
}
