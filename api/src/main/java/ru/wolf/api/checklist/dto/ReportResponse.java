package ru.wolf.api.checklist.dto;

public record ReportResponse(LocalDate from, LocalDate to, List<DayResponse> days, int checkedTotal, int itemsTotal, int distractionsTotal) {}
