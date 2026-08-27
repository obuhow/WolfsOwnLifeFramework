package ru.wolf.api.timeentry.dto;

import java.util.List;

public record TodayResponse(
        String date,
        String timezone,
        String dayStart,
        String dayEnd,
        String dayEndSetting,
        List<TimeEntryResponse> entries,
        int dayNormMinutes,
        int dayFactMinutes,
        int remainingMinutes
) {
    /** Compatibility accessor for legacy callers. */
    public String getDate() { return date(); }

    /** Compatibility accessor for legacy callers. */
    public String getTimezone() { return timezone(); }

    /** Compatibility accessor for legacy callers. */
    public String getDayStart() { return dayStart(); }

    /** Compatibility accessor for legacy callers. */
    public String getDayEnd() { return dayEnd(); }

    /** Compatibility accessor for legacy callers. */
    public String getDayEndSetting() { return dayEndSetting(); }

    /** Compatibility accessor for legacy callers. */
    public List<TimeEntryResponse> getEntries() { return entries(); }

    /** Compatibility accessor for legacy callers. */
    public int getDayNormMinutes() { return dayNormMinutes(); }

    /** Compatibility accessor for legacy callers. */
    public int getDayFactMinutes() { return dayFactMinutes(); }

    /** Compatibility accessor for legacy callers. */
    public int getRemainingMinutes() { return remainingMinutes(); }
}
