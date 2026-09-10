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

import java.time.LocalDate;
import java.util.List;

/**
 * Release 1.4 ticket 02 (decision И-I): what the uploaded schedule WOULD create, computed without
 * writing anything to the database.
 *
 * <p>The previous import wrote entries the moment a file was uploaded, so a user only learned what
 * had happened after it had happened. The preview reverses that: parse → show the summary → let the
 * user press «Применить». Every number here is counted by code from the parsed cells, never
 * estimated.
 *
 * @param weeks               sheets that yielded a dated week
 * @param totalCells          filled activity cells found (each becomes one 15-minute Запись времени)
 * @param from                earliest date in the file
 * @param to                  latest date in the file
 * @param knownActivities     distinct activities already mapped to a Дело (`activity_mapping`)
 * @param newActivities       distinct activities with no mapping yet — these raise import questions
 * @param timeEntriesToCreate how many Записи времени «Применить» would create
 * @param alreadyImported     true when a run with this file's hash already exists, so applying again
 *                            would be a repeat (dedup by hash is kept from the original import)
 * @param activities          per-activity breakdown, most frequent first
 */
public record ImportPreviewResponse(
        int weeks,
        int totalCells,
        LocalDate from,
        LocalDate to,
        int knownActivities,
        int newActivities,
        int timeEntriesToCreate,
        boolean alreadyImported,
        List<ActivityPreview> activities
) {

    /**
     * One distinct activity text as it appears in the file.
     *
     * @param activityText the text exactly as written in the sheet
     * @param cells        how many cells carry it
     * @param known        whether it already maps to a Дело
     * @param deloTitle    title of the mapped Дело, or {@code null} when the activity is new
     */
    public record ActivityPreview(String activityText, int cells, boolean known, String deloTitle) {
    }
}
