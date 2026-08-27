package ru.wolf.api.gantt.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
public record ForecastResponse(Long projectId,LocalDate planEnd,LocalDate forecastEnd,BigDecimal weeklyAvg,BigDecimal remaining) {}
