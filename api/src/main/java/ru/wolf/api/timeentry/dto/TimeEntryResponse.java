package ru.wolf.api.timeentry.dto;

import ru.wolf.api.delo.Delo;
import ru.wolf.api.timeentry.TimeEntry;

public record TimeEntryResponse(
        Long id,
        Long deloId,
        String deloTitle,
        Delo.ExecutionMode deloExecutionMode,
        String adHocText,
        String startAt,
        String endAt,
        TimeEntry.Status status
) {
    /** Compatibility accessor for legacy callers. */
    public Long getId() { return id(); }

    /** Compatibility accessor for legacy callers. */
    public Long getDeloId() { return deloId(); }

    /** Compatibility accessor for legacy callers. */
    public String getDeloTitle() { return deloTitle(); }

    /** Compatibility accessor for legacy callers. */
    public Delo.ExecutionMode getDeloExecutionMode() { return deloExecutionMode(); }

    /** Compatibility accessor for legacy callers. */
    public String getAdHocText() { return adHocText(); }

    /** Compatibility accessor for legacy callers. */
    public String getStartAt() { return startAt(); }

    /** Compatibility accessor for legacy callers. */
    public String getEndAt() { return endAt(); }

    /** Compatibility accessor for legacy callers. */
    public TimeEntry.Status getStatus() { return status(); }
}
