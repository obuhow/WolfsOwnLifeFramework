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
package ru.wolf.api.project.dto;

import ru.wolf.api.aggregate.FactAggregate;
import ru.wolf.api.project.Project;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProjectDetailResponse(
        Long id,
        Long lifeAreaId,
        String lifeAreaName,
        Long parentId,
        String parentTitle,
        String title,
        Project.Status status,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalPlanHours,
        Project.PlanDistribution planDistribution,
        List<DeloLink> delos,
        FactAggregate aggregates
) {
}
