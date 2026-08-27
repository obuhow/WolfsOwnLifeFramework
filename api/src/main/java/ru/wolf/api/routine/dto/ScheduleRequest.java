package ru.wolf.api.routine.dto;

import jakarta.validation.constraints.NotNull;

public record ScheduleRequest(
        @NotNull String dayOfWeek,
        @NotNull String startTime,
        @NotNull String endTime
) {
    /** Compatibility accessor for legacy callers. */
    public @NotNull String getDayOfWeek() { return dayOfWeek(); }

    /** Compatibility accessor for legacy callers. */
    public @NotNull String getStartTime() { return startTime(); }

    /** Compatibility accessor for legacy callers. */
    public @NotNull String getEndTime() { return endTime(); }
}
