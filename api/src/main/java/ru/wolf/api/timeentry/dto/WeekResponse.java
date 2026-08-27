package ru.wolf.api.timeentry.dto;

import java.util.List;

public record WeekResponse(
        int isoYear,
        int isoWeek,
        String weekStart,
        String weekEndExclusive,
        String rangeStart,
        String rangeEnd,
        String timezone,
        String dayEndSetting,
        List<WeekDayInfo> days,
        List<TimeEntryResponse> entries
) {
    /** Compatibility accessor for legacy callers. */
    public int getIsoYear() { return isoYear(); }

    /** Compatibility accessor for legacy callers. */
    public int getIsoWeek() { return isoWeek(); }

    /** Compatibility accessor for legacy callers. */
    public String getWeekStart() { return weekStart(); }

    /** Compatibility accessor for legacy callers. */
    public String getWeekEndExclusive() { return weekEndExclusive(); }

    /** Compatibility accessor for legacy callers. */
    public String getRangeStart() { return rangeStart(); }

    /** Compatibility accessor for legacy callers. */
    public String getRangeEnd() { return rangeEnd(); }

    /** Compatibility accessor for legacy callers. */
    public String getTimezone() { return timezone(); }

    /** Compatibility accessor for legacy callers. */
    public String getDayEndSetting() { return dayEndSetting(); }

    /** Compatibility accessor for legacy callers. */
    public List<WeekDayInfo> getDays() { return days(); }

    /** Compatibility accessor for legacy callers. */
    public List<TimeEntryResponse> getEntries() { return entries(); }
}
