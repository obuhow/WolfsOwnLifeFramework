package ru.wolf.api.checklist.dto;
import java.time.LocalDate;
import java.util.List;



public record ReportResponse(LocalDate from, LocalDate to, List<DayResponse> days, int checkedTotal, int itemsTotal, int distractionsTotal) {}
