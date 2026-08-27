package ru.wolf.api.routine.dto;

public record ScheduleResponse(Long id, String dayOfWeek, String startTime, String endTime) {
    /** Compatibility accessor for legacy callers. */
    public Long getId() { return id(); }

    /** Compatibility accessor for legacy callers. */
    public String getDayOfWeek() { return dayOfWeek(); }

    /** Compatibility accessor for legacy callers. */
    public String getStartTime() { return startTime(); }

    /** Compatibility accessor for legacy callers. */
    public String getEndTime() { return endTime(); }
}
