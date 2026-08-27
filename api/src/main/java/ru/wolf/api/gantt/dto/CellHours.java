package ru.wolf.api.gantt.dto;
import java.math.BigDecimal;
public record CellHours(int isoYear,int isoWeek,BigDecimal planHours,BigDecimal factHours) {}
