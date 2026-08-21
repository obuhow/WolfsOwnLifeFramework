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
            sheet("routines", "externalId", "title", "description", "weeklyHours", "color", "icon", "archived"),
            sheet("routine_schedules", "externalId", "routineExternalId", "dayOfWeek", "startTime", "endTime"),
            sheet("delos", "externalId", "title", "description", "executionMode", "recurrenceWeekdays", "recurrenceWindowStart", "recurrenceWindowEnd", "recurrenceSlots", "projectExternalIds", "primaryProjectExternalId"),
            sheet("time_entries", "externalId", "deloExternalId", "adHocText", "startAt", "endAt", "status"),
            sheet("goals", "externalId", "title", "description", "priority", "archived"),
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
