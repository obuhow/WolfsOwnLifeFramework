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
 * You should have received this program with a copy of the GNU Affero
 * General Public License along with this program. If not see
 * <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.project.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

import ru.wolf.api.project.Project;

public record UpdateProjectRequest(
        @NotNull Long lifeAreaId,
        Long parentId,
        @NotBlank @Size(max = 200) String title,
        Project.Status status,
        @Size(max = 10000) String description,
        LocalDate startDate,
        LocalDate endDate,
        @DecimalMin(value = "0.0", inclusive = true) BigDecimal totalPlanHours,
        Project.PlanDistribution planDistribution
) {
    public UpdateProjectRequest(Long lifeAreaId, Long parentId, String title, Project.Status status,
                                String description, LocalDate startDate, LocalDate endDate,
                                BigDecimal totalPlanHours) {
        this(lifeAreaId, parentId, title, status, description, startDate, endDate, totalPlanHours, null);
    }
}
