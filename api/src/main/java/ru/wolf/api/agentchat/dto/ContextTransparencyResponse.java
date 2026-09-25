package ru.wolf.api.agentchat.dto;

import ru.wolf.api.agentcontext.AgentContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Safe, structured description of the context included in one agent request. */
public record ContextTransparencyResponse(
        boolean available,
        String reason,
        Period period,
        List<Project> projects,
        List<Goal> goals,
        List<Routine> routines,
        Dynamics dynamics,
        int historyMessages,
        int payloadCharacters
) {
    public ContextTransparencyResponse {
        projects = List.copyOf(projects);
        goals = List.copyOf(goals);
        routines = List.copyOf(routines);
    }

    public static ContextTransparencyResponse from(
            AgentContext context, int historyMessages, int payloadCharacters) {
        return new ContextTransparencyResponse(
                true,
                null,
                new Period(context.period().from(), context.period().toExclusive(), context.period().weeks()),
                context.projects().stream()
                        .map(project -> new Project(project.title(), project.lifeArea(), project.totalPlanHours()))
                        .toList(),
                context.goals().stream()
                        .map(goal -> new Goal(goal.title(), goal.priority(), goal.projectTitles()))
                        .toList(),
                context.routines().stream()
                        .map(routine -> new Routine(routine.title(), routine.weeklyHours(), routine.schedule()))
                        .toList(),
                Dynamics.from(context.dynamics()),
                historyMessages,
                payloadCharacters
        );
    }

    public static ContextTransparencyResponse unavailable(String reason) {
        return new ContextTransparencyResponse(false, reason, null, List.of(), List.of(), List.of(), null, 0, 0);
    }

    public record Period(LocalDate from, LocalDate toExclusive, int weeks) {
    }

    public record Project(String title, String lifeArea, BigDecimal totalPlanHours) {
    }

    public record Goal(String title, int priority, List<String> projectTitles) {
        public Goal {
            projectTitles = List.copyOf(projectTitles);
        }
    }

    public record Routine(String title, BigDecimal weeklyHours, List<String> schedule) {
        public Routine {
            schedule = List.copyOf(schedule);
        }
    }

    public record Dynamics(
            boolean historyAvailable,
            String historyNote,
            List<Hours> projectHours,
            List<Hours> lifeAreaHours,
            List<Hours> routineHours,
            Trend trend,
            List<Week> weeks
    ) {
        public Dynamics {
            projectHours = List.copyOf(projectHours);
            lifeAreaHours = List.copyOf(lifeAreaHours);
            routineHours = List.copyOf(routineHours);
            weeks = List.copyOf(weeks);
        }

        static Dynamics from(AgentContext.ScheduleDynamics dynamics) {
            return new Dynamics(
                    dynamics.historyAvailable(),
                    dynamics.historyNote(),
                    dynamics.projectHours().stream().map(Hours::from).toList(),
                    dynamics.lifeAreaHours().stream().map(Hours::from).toList(),
                    dynamics.routineHours().stream().map(Hours::from).toList(),
                    dynamics.trend() == null ? null : new Trend(
                            dynamics.trend().direction(),
                            dynamics.trend().changeHours(),
                            dynamics.trend().firstHalfFact(),
                            dynamics.trend().secondHalfFact()),
                    dynamics.weeks().stream().map(Week::from).toList()
            );
        }
    }

    public record Hours(Long id, String label, BigDecimal planned, BigDecimal fact, BigDecimal pending) {
        static Hours from(AgentContext.Hours hours) {
            return new Hours(hours.id(), hours.label(), hours.planned(), hours.fact(), hours.pending());
        }
    }

    public record Trend(
            String direction,
            BigDecimal changeHours,
            BigDecimal firstHalfFact,
            BigDecimal secondHalfFact
    ) {
    }

    public record Week(
            String weekId,
            BigDecimal plannedHours,
            BigDecimal factHours,
            BigDecimal pendingHours,
            BigDecimal routinePlannedHours,
            BigDecimal routineFactHours,
            BigDecimal routinePendingHours
    ) {
        static Week from(AgentContext.WeekDynamics week) {
            return new Week(week.weekId(), week.plannedHours(), week.factHours(), week.pendingHours(),
                    week.routinePlannedHours(), week.routineFactHours(), week.routinePendingHours());
        }
    }
}
