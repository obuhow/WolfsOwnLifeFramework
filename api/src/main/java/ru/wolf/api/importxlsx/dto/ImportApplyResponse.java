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
package ru.wolf.api.importxlsx.dto;

/**
 * Release 1.4 ticket 02: what happened when the user pressed «Применить».
 *
 * <p>Mirrors the preview so the UI can show "created N of M" without re-reading the run, and
 * carries the run id so the existing questions/resolve screens keep working unchanged.
 *
 * @param importRunId      id of the {@code xlsx_import_run} written by this apply
 * @param created          Записи времени actually inserted
 * @param skippedOccupied  slots left untouched because a Запись already occupied them (SKIP_ALL)
 * @param overwritten      existing Записи времени replaced by the file's data (OVERWRITE_ALL)
 * @param pendingQuestions unresolved import questions after the run (drives the resolve flow)
 * @param alreadyImported  true when the file hash matched an earlier run, so nothing was written
 * @param cancelled        true when the chosen strategy was CANCEL, so nothing was written
 */
public record ImportApplyResponse(
        Long importRunId,
        int created,
        int skippedOccupied,
        int overwritten,
        int pendingQuestions,
        boolean alreadyImported,
        boolean cancelled
) {
}
