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
 * along with this program if not, see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.delo.dto;

import ru.wolf.api.aggregate.FactAggregate;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

public record DeloDetailResponse(
        Long id,
        String title,
        String description,
        ru.wolf.api.delo.Delo.ExecutionMode executionMode,
        List<ProjectLink> projects,
        Instant createdAt,
        Instant updatedAt,
        FactAggregate aggregates,
        List<DayOfWeek> recurrenceWeekdays,
        LocalTime recurrenceWindowStart,
        LocalTime recurrenceWindowEnd,
        List<RecurrenceSlotDto> recurrenceSlots
) {
}
