package ru.wolf.api.timeentry.dto;

import java.util.List;

public record EnsureSleepResponse(int filledCount, Long sleepDeloId, List<TimeEntryResponse> entries) {
    /** Compatibility accessor for legacy callers. */
    public int getFilledCount() { return filledCount(); }

    /** Compatibility accessor for legacy callers. */
    public Long getSleepDeloId() { return sleepDeloId(); }

    /** Compatibility accessor for legacy callers. */
    public List<TimeEntryResponse> getEntries() { return entries(); }
}
