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

import java.math.BigDecimal;
import java.time.LocalTime;

public record UserSettingsResponse(
        String timezone,
        LocalTime nightStart,
        LocalTime nightEnd,
        LocalTime dayEnd,
        LocalTime defaultSleepEnd,
        String hourAccountingMode,
        String timeCaptureMode,
        BigDecimal availableWeeklyHours
) {

    public static UserSettingsResponse from(ru.wolf.api.user.User user) {
        return new UserSettingsResponse(
                user.getTimezone(),
                user.getNightStart(),
                user.getNightEnd(),
                user.getDayEnd(),
                user.getDefaultSleepEnd(),
                user.getHourAccountingMode(),
                user.getTimeCaptureMode(),
                user.getAvailableWeeklyHours()
        );
    }
}
