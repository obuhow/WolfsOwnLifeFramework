package ru.wolf.api.lifesphere.dto;

import ru.wolf.api.lifesphere.Synergy;

public record SynergyResponse(Long id, Long projectId, Long ideaId, Long routineId, Long sphereId,
                              String sphereName, String sphereColor, Synergy.Impact impact) {
    public static SynergyResponse from(Synergy synergy) {
        return new SynergyResponse(synergy.getId(),
                synergy.getProject() == null ? null : synergy.getProject().getId(),
                synergy.getIdeaId(), synergy.getRoutine() == null ? null : synergy.getRoutine().getId(),
                synergy.getSphere().getId(), synergy.getSphere().getName(), synergy.getSphere().getColor(), synergy.getImpact());
    }
}
