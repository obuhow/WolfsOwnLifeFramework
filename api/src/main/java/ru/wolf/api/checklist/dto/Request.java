package ru.wolf.api.checklist.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record Request(LocalDate date, @NotBlank String title, Long deloId, Integer position) {
}
