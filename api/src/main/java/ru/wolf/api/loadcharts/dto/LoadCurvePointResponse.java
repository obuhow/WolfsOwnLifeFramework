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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** Точка ступенчатой кривой нагрузки проекта (release 0.8). */
public record LoadCurvePointResponse(
        Long id,
        LocalDate weekStart,
        BigDecimal hours
) {
    public static LoadCurvePointResponse from(LoadCurveEntry e) {
        return new LoadCurvePointResponse(
                e.getId(),
                e.getWeekStart(),
                e.getHours() == null ? null : e.getHours().setScale(2, RoundingMode.HALF_UP)
        );
    }
}
