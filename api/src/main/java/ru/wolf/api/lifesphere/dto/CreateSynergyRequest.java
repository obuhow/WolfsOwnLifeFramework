package ru.wolf.api.lifesphere.dto;

import jakarta.validation.constraints.NotNull;
import ru.wolf.api.lifesphere.Synergy;

public record CreateSynergyRequest(@NotNull Long sphereId, Long projectId, Long ideaId, Long routineId,
                                   @NotNull Synergy.Impact impact) {}
