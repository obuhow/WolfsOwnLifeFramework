package ru.wolf.api.calendar.dto;

import ru.wolf.api.timeentry.TimeEntry;
import java.time.*;
import java.util.List;

public record MonthResponse(String month, LocalDate gridStart, LocalDate gridEnd, List<DayResponse> days) {}
