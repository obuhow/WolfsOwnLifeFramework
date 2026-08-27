package ru.wolf.api.timeentry.dto;

import java.util.List;

public record ConfirmAllResponse(int confirmedCount, List<TimeEntryResponse> entries) {
    /** Compatibility accessor for legacy callers. */
    public int getConfirmedCount() { return confirmedCount(); }

    /** Compatibility accessor for legacy callers. */
    public List<TimeEntryResponse> getEntries() { return entries(); }
}
