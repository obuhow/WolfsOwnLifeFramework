/*
 * WOLF — Wolf's Own Life Framework
 * Copyright (C) 2025 Pavel Obukhov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.importxlsx;

/**
 * How an overlapping import resolves the weeks that already carry Записи времени.
 *
 * <p>Decision И-G/H: when the file being imported overlaps slots the user already has, the
 * system must not silently write or silently duplicate. Instead it reports the count of
 * conflicting cells in the preview and lets the user make ONE conscious choice for the whole
 * import — there can be hundreds of conflicts, so per-cell questions (И-G, в2/в3) are out of
 * scope for the MVP. The strategies are:</p>
 *
 * <ul>
 *   <li>{@link #SKIP_ALL} — leave every occupied slot untouched, create only the free cells.</li>
 *   <li>{@link #OVERWRITE_ALL} — replace the existing Запись in each conflicting cell with the
 *       file's data.</li>
 *   <li>{@link #CANCEL} — write nothing, the import is aborted.</li>
 * </ul>
 *
 * <p>A conflict is defined as a slot already covered by ANY Запись времени (И-H); the activity
 * text in the file is not compared to what is already there.</p>
 */
public enum ImportConflictStrategy {
    /** Keep existing facts; only free cells are imported. */
    SKIP_ALL,
    /** Replace each conflicting slot's Запись time and (if any) Дело with the file's data. */
    OVERWRITE_ALL,
    /** Abort the whole import: nothing is written. */
    CANCEL
}
