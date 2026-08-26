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
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.user.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record UpdateSettingsRequest(
        @NotBlank String timezone,
        @NotBlank String nightStart,
        @NotBlank String nightEnd,
        /** Optional for backward compat; default 02:00 on entity */
        String dayEnd,
        String defaultSleepEnd,
        @NotBlank String hourAccountingMode,
        String timeCaptureMode,
        @DecimalMin(value = "0.0", inclusive = true) BigDecimal availableWeeklyHours
) {
}
