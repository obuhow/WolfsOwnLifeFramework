package ru.wolf.api.today.dto;

public record BacklogResponse(String weekId, List<ItemResponse> items, BigDecimal totalPlanned, BigDecimal totalFact) {}
