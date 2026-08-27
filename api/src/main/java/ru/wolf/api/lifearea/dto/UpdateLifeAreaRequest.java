package ru.wolf.api.lifearea.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateLifeAreaRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 7) String color
) {}
