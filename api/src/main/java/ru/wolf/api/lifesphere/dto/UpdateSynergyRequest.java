package ru.wolf.api.lifesphere.dto;

import jakarta.validation.constraints.NotNull;
import ru.wolf.api.lifesphere.Synergy;

public record UpdateSynergyRequest(@NotNull Synergy.Impact impact) {}
