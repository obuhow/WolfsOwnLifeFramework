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
package ru.wolf.api.importer.dto;

import java.util.List;

/**
 * A candidate WOLF entity parsed from free text. Each field is annotated with
 * its own confidence so the calling channel can render a preview card:
 * confident fields are applied immediately, field candidates marked
 * {@link Confidence#NEEDS_CONFIRMATION} are shown to the user for editing.
 */
public record ParsedCandidate(
        EntityKind kind,
        List<ParsedField> fields
) {
}
