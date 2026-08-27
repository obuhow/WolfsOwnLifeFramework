package ru.wolf.api.lifesphere.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLifeSphereRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 7) String color
) {}
