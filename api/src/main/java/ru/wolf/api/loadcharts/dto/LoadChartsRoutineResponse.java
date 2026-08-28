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

import ru.wolf.api.routine.Routine;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Рутина в суммарной загрузке (release 0.8). Дорожек и прогноза финиша не имеет. */
public record LoadChartsRoutineResponse(
        Long id,
        String title,
        BigDecimal weeklyHours
) {
    public static LoadChartsRoutineResponse from(Routine r) {
        return new LoadChartsRoutineResponse(
                r.getId(),
                r.getTitle(),
                r.getWeeklyHours() == null ? null : r.getWeeklyHours().setScale(2, RoundingMode.HALF_UP)
        );
    }
}
