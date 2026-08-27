package ru.wolf.api.checklist.dto;

public record DayResponse(LocalDate date, List<ItemResponse> items, List<DistractionResponse> distractions, int checkedCount, int totalCount) {}
