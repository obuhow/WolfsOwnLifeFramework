package ru.wolf.api.timeentry.dto;

import java.util.List;

/** Response actions: SHRINK, EXTEND, SPLIT, PLACE, DELETE, NEED_PICKER. */
public record GridClickResponse(String action, List<TimeEntryResponse> entries, String message) {
    /** Compatibility accessor for legacy callers. */
    public String getAction() { return action(); }

    /** Compatibility accessor for legacy callers. */
    public List<TimeEntryResponse> getEntries() { return entries(); }

    /** Compatibility accessor for legacy callers. */
    public String getMessage() { return message(); }
}
