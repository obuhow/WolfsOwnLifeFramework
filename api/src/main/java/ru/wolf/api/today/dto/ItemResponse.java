package ru.wolf.api.today.dto;

public record ItemResponse(Long deloId, String title, BigDecimal plannedHours, BigDecimal factHours, String projectTitle) {}
