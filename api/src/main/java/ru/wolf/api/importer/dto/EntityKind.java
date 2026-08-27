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

/** Kind of WOLF entity a parsed candidate may become. */
public enum EntityKind {
    /** Дело — the default entity created from free text. */
    DELO,
    /** Проект — only when explicitly named in the text («в проекте Х»). */
    PROJECT,
    /** Рутина — only when explicitly requested («как рутину»). */
    ROUTINE,
    /** Повторяющееся Дело — via RecurrenceService, the default for recurring phrasing. */
    RECURRENCE
}
