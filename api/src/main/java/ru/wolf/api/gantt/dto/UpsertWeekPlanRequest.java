package ru.wolf.api.gantt.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record UpsertWeekPlanRequest(@NotNull Long projectId,@NotNull Integer isoYear,@NotNull @Min(1) @Max(53) Integer isoWeek,@DecimalMin(value="0.0",inclusive=true) BigDecimal planHours) {}
