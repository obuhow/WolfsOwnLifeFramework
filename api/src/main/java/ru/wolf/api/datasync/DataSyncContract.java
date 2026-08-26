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
package ru.wolf.api.datasync;

import java.util.List;
import java.util.Map;

public final class DataSyncContract {
    public static final String FORMAT = "wolf-data";
    public static final String VERSION = "0.21";

    private static final List<Sheet> SHEETS = List.of(
            sheet("life_areas", "externalId", "name", "sortOrder", "color"),
            sheet("life_spheres", "externalId", "name", "sortOrder", "color", "archived"),
            sheet("projects", "externalId", "lifeAreaExternalId", "parentExternalId", "title", "status", "description", "startDate", "endDate", "totalPlanHours", "planDistribution", "planFrozenAt"),
            sheet("routines", "externalId", "title", "description", "weeklyHours", "color", "icon", "archived", "goalExternalIds"),
            sheet("routine_schedules", "externalId", "routineExternalId", "dayOfWeek", "startTime", "endTime"),
            sheet("delos", "externalId", "title", "description", "executionMode", "recurrenceWeekdays", "recurrenceWindowStart", "recurrenceWindowEnd", "recurrenceSlots", "projectExternalIds", "primaryProjectExternalId"),
            sheet("time_entries", "externalId", "deloExternalId", "adHocText", "startAt", "endAt", "status"),
            sheet("goals", "externalId", "title", "description", "priority", "archived", "projectExternalIds"),
            sheet("goal_metrics", "externalId", "goalExternalId", "kind", "value", "targetValue", "at"),
            sheet("goal_week_budgets", "externalId", "goalExternalId", "isoYear", "isoWeek", "hours"),
            sheet("ideas", "externalId", "title", "description", "category", "status", "promotedProjectExternalId"),
            sheet("notes", "externalId", "projectExternalId", "deloExternalId", "author", "body", "tags", "audioRef", "audioContentType", "audioFilename"),
            sheet("synergies", "externalId", "projectExternalId", "ideaExternalId", "routineExternalId", "sphereExternalId", "impact"),
            sheet("project_dependencies", "externalId", "blockerExternalId", "blockedExternalId"),
            sheet("backlog_items", "externalId", "deloExternalId", "scope", "periodId", "plannedHours", "position", "movedToWeek"),
            sheet("checklist_items", "externalId", "date", "title", "deloExternalId", "position", "done", "doneAt"),
            sheet("activity_mappings", "externalId", "activityText", "deloExternalId")
    );

    private DataSyncContract() { }

    public static Manifest manifest() {
        return new Manifest(FORMAT, VERSION, SHEETS, Map.of(
                "date", "YYYY-MM-DD",
                "localDateTime", "YYYY-MM-DDTHH:mm",
                "interval", "[startAt,endAt)",
                "listSeparator", "|",
                "json", "JSON text in one cell"));
    }

    private static Sheet sheet(String name, String... columns) {
        return new Sheet(name, List.of(columns));
    }

    public record Manifest(String format, String version, List<Sheet> sheets, Map<String, String> conventions) { }
    public record Sheet(String name, List<String> columns) { }
}
