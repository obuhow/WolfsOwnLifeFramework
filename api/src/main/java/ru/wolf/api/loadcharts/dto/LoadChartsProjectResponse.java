/*
 * WOLF — Wolf's Own Life Framework
 * Copyright (C) 2025 Pavel Obukhov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.loadcharts.dto;

import ru.wolf.api.loadcurve.LoadCurveEntry;
import ru.wolf.api.project.Project;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/** Один проект в ответе load-charts (release 0.8). */
public record LoadChartsProjectResponse(
        Long id,
        String title,
        Long lifeAreaId,
        String lifeAreaName,
        long deloCount,
        BigDecimal effortHours,
        String effortSource,
        BigDecimal accumulatedFactHours,
        BigDecimal remainingHours,
        BigDecimal weeklyPlanHours,
        List<LoadCurvePointResponse> curve,
        LocalDate forecastByCurve,
        LocalDate forecastByRate
) {
    public static LoadChartsProjectResponse build(
            Project p,
            long deloCount,
            BigDecimal effortHours,
            String effortSource,
            BigDecimal accumulatedFactHours,
            BigDecimal remainingHours,
            BigDecimal weeklyPlanHours,
            List<LoadCurveEntry> curve,
            LocalDate forecastByCurve,
            LocalDate forecastByRate
    ) {
        return new LoadChartsProjectResponse(
                p.getId(),
                p.getTitle(),
                p.getLifeArea() != null ? p.getLifeArea().getId() : null,
                p.getLifeArea() != null ? p.getLifeArea().getName() : null,
                deloCount,
                effortHours.setScale(2, RoundingMode.HALF_UP),
                effortSource,
                accumulatedFactHours.setScale(2, RoundingMode.HALF_UP),
                remainingHours.setScale(2, RoundingMode.HALF_UP),
                weeklyPlanHours.setScale(2, RoundingMode.HALF_UP),
                curve.stream().map(LoadCurvePointResponse::from).toList(),
                forecastByCurve,
                forecastByRate
        );
    }
}
