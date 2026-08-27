package ru.wolf.api.calendar.dto;

import ru.wolf.api.timeentry.TimeEntry;
import java.time.*;
import java.util.List;

public record DayResponse(LocalDate date, boolean outOfMonth, List<EntryResponse> entries, int totalCount) {}
