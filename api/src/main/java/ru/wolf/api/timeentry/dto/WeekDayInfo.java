package ru.wolf.api.timeentry.dto;

public record WeekDayInfo(String date, String weekday, String dayStart, String dayEnd) {
    /** Compatibility accessor for legacy callers. */
    public String getDate() { return date(); }

    /** Compatibility accessor for legacy callers. */
    public String getWeekday() { return weekday(); }

    /** Compatibility accessor for legacy callers. */
    public String getDayStart() { return dayStart(); }

    /** Compatibility accessor for legacy callers. */
    public String getDayEnd() { return dayEnd(); }
}
