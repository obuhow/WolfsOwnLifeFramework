package ru.wolf.api.lifearea.dto;

import ru.wolf.api.lifearea.LifeArea;

public record LifeAreaResponse(Long id, String name, int sortOrder, String color) {
    public static LifeAreaResponse from(LifeArea area) {
        return new LifeAreaResponse(area.getId(), area.getName(), area.getSortOrder(), area.getColor());
    }
}
