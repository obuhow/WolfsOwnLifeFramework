package ru.wolf.api.calendar.dto;

import ru.wolf.api.timeentry.TimeEntry;
import java.time.*;
import java.util.List;

public record EntryResponse(Long id, LocalDateTime startAt, Long deloId, String deloTitle, Long projectId, String projectTitle, TimeEntry.Status status) {}
