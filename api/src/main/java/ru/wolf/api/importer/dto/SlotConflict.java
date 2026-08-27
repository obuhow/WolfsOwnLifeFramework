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

import java.time.LocalDateTime;
import java.util.List;

/**
 * A conflict between a parsed candidate's time slot and an existing
 * {@code TimeEntry}. The service only detects conflicts and lists the three
 * resolution options; the calling channel chooses how to resolve.
 */
public record SlotConflict(
        int candidateIndex,
        LocalDateTime slotStart,
        LocalDateTime slotEnd,
        Long existingEntryId,
        List<ResolutionOption> options
) {

    public static SlotConflict of(int candidateIndex, LocalDateTime slotStart, LocalDateTime slotEnd,
                                  Long existingEntryId) {
        return new SlotConflict(candidateIndex, slotStart, slotEnd, existingEntryId,
                List.of(ResolutionOption.REPLACE, ResolutionOption.SHIFT, ResolutionOption.CREATE_OVER));
    }
}
