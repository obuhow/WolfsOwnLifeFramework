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
 * Result of parsing free text into WOLF entity candidates.
 *
 * <p>{@link #unparsed} is {@code true} when the text could not be parsed at all
 * (empty input, or the LLM returned invalid JSON twice). In that case
 * {@link #candidates()} is empty and {@link #clarificationQuestion()} carries a
 * single clarifying question for the user — the service never throws an error
 * to the caller for unparseable input.
 */
public record ParseResult(
        boolean unparsed,
        String clarificationQuestion,
        List<ParsedCandidate> candidates,
        List<SlotConflict> conflicts
) {

    public static ParseResult unparsed(String clarificationQuestion) {
        return new ParseResult(true, clarificationQuestion, List.of(), List.of());
    }

    public static ParseResult parsed(List<ParsedCandidate> candidates, List<SlotConflict> conflicts) {
        return new ParseResult(false, null, candidates, conflicts);
    }
}
