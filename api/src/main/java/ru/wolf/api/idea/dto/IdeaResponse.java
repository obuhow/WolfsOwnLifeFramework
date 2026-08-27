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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.idea.dto;

import ru.wolf.api.idea.Idea;

public record IdeaResponse(
        Long id,
        String title,
        String description,
        Idea.Category category,
        Idea.Status status,
        Long promotedProjectId,
        String promotedProjectTitle
) {

    public static IdeaResponse from(Idea idea) {
        return new IdeaResponse(
                idea.getId(),
                idea.getTitle(),
                idea.getDescription(),
                idea.getCategory(),
                idea.getStatus(),
                idea.getPromotedProject() == null ? null : idea.getPromotedProject().getId(),
                idea.getPromotedProject() == null ? null : idea.getPromotedProject().getTitle()
        );
    }
}
