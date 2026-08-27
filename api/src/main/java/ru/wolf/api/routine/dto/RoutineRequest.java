package ru.wolf.api.routine.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record RoutineRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 10000) String description,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal weeklyHours,
        @Size(max = 7) String color,
        @Size(max = 50) String icon
) {
    public String getTitle() { return title(); }
    public String getDescription() { return description(); }
    public BigDecimal getWeeklyHours() { return weeklyHours(); }
    public String getColor() { return color(); }
    public String getIcon() { return icon(); }
}
