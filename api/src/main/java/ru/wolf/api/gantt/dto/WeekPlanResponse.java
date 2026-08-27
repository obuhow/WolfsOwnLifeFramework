package ru.wolf.api.gantt.dto;
import java.math.BigDecimal;
public record WeekPlanResponse(Long projectId,Integer isoYear,Integer isoWeek,BigDecimal planHours) {}
