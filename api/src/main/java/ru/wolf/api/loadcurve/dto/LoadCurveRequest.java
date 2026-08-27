package ru.wolf.api.loadcurve.dto;
import jakarta.validation.constraints.*; import java.math.BigDecimal; import java.time.LocalDate;
public record LoadCurveRequest(@NotNull LocalDate weekStart,@NotNull @DecimalMin("0.0") BigDecimal hours,LocalDate endWeek) {}
