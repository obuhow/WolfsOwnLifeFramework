package ru.wolf.api.routine.dto;

import java.math.BigDecimal;
import java.util.List;

public record RoutineResponse(
        Long id,
        String title,
        String description,
        BigDecimal weeklyHours,
        String color,
        String icon,
        boolean archived,
        List<ScheduleResponse> schedules,
        List<Long> goalIds,
        List<GoalLinkResponse> goals
) {
    /** Compatibility accessor for legacy callers. */
    public Long getId() { return id(); }

    /** Compatibility accessor for legacy callers. */
    public String getTitle() { return title(); }

    /** Compatibility accessor for legacy callers. */
    public String getDescription() { return description(); }

    /** Compatibility accessor for legacy callers. */
    public BigDecimal getWeeklyHours() { return weeklyHours(); }

    /** Compatibility accessor for legacy callers. */
    public String getColor() { return color(); }

    /** Compatibility accessor for legacy callers. */
    public String getIcon() { return icon(); }

    /** Compatibility accessor for legacy callers. */
    public boolean isArchived() { return archived(); }

    /** Compatibility accessor for legacy callers. */
    public List<ScheduleResponse> getSchedules() { return schedules(); }

    /** Compatibility accessor for legacy callers. */
    public List<Long> getGoalIds() { return goalIds(); }

    /** Compatibility accessor for legacy callers. */
    public List<GoalLinkResponse> getGoals() { return goals(); }
}
