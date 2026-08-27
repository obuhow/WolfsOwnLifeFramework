package ru.wolf.api.loadcurve.dto;
import java.math.BigDecimal; import java.time.LocalDate;
public record LoadCurveResponse(Long id,LocalDate weekStart,BigDecimal hours,String ownerType) {}
