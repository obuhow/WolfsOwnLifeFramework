package ru.wolf.api.checklist.dto;
import java.time.LocalDate;
import java.util.List;



public record DayResponse(LocalDate date, List<ItemResponse> items, List<DistractionResponse> distractions, int checkedCount, int totalCount) {}
