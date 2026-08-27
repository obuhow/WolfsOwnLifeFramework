package ru.wolf.api.lifesphere.dto;

import ru.wolf.api.lifesphere.LifeSphere;

public record LifeSphereResponse(Long id, String name, int sortOrder, String color, boolean archived) {
    public static LifeSphereResponse from(LifeSphere sphere) {
        return new LifeSphereResponse(sphere.getId(), sphere.getName(), sphere.getSortOrder(), sphere.getColor(), sphere.isArchived());
    }
}
