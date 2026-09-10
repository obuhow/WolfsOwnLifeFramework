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

/**
 * A WOLF entity created by a {@code /import/confirm} call. {@code type} is a
 * coarse kind for the UI; {@code link} is a deep link into the SPA.
 *
 * <p>Release 1.3 ticket 01 (bug Б-1) added the schedule feedback fields. Previously the response
 * said only <em>which entities</em> were created, so a recurrence that produced zero time entries
 * was indistinguishable from one that filled twelve weeks — the silent no-op the bug describes.
 *
 * @param timeEntriesCreated how many Записи времени this entity actually placed on the schedule;
 *                           {@code 0} for entities that place none by nature (Проект, Рутина)
 * @param note               a short human-readable remark shown next to the entity when there is
 *                           something the user must know (nothing was scheduled, part of the slots
 *                           were skipped, the stated day/time could not be read); {@code null} when
 *                           the entity was created exactly as asked
 */
public record CreatedEntity(
        EntityKind kind,
        Long id,
        String title,
        String type,
        String link,
        int timeEntriesCreated,
        String note
) {

    /** Entity that schedules nothing by nature (Проект, Рутина) or a plain Дело without a slot. */
    public static CreatedEntity of(EntityKind kind, Long id, String title, String type, String link) {
        return new CreatedEntity(kind, id, title, type, link, 0, null);
    }

    /** Entity created together with {@code timeEntriesCreated} time entries on the schedule. */
    public static CreatedEntity scheduled(EntityKind kind, Long id, String title, String type, String link,
                                          int timeEntriesCreated, String note) {
        return new CreatedEntity(kind, id, title, type, link, timeEntriesCreated, note);
    }
}
