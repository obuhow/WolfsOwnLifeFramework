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

import java.util.Map;

/**
 * A single parsed field of a candidate entity. The string value carries the
 * resolved (possibly substituted) value; confidence marks whether it was
 * explicit in the text ({@link Confidence#CONFIDENT}) or inferred
 * ({@link Confidence#NEEDS_CONFIRMATION}).
 */
public record ParsedField(String name, String value, Confidence confidence) {

    public static ParsedField confident(String name, String value) {
        return new ParsedField(name, value, Confidence.CONFIDENT);
    }

    public static ParsedField needsConfirmation(String name, String value) {
        return new ParsedField(name, value, Confidence.NEEDS_CONFIRMATION);
    }

    public Map.Entry<String, String> toEntry() {
        return Map.entry(name, value);
    }
}
