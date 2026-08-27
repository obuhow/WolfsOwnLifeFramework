package ru.wolf.api.checklist.dto;

import java.time.Instant;
import java.time.LocalDate;

public record Response(Long id, LocalDate date, String title, Long deloId, boolean done, Instant doneAt, Integer position) {
}
