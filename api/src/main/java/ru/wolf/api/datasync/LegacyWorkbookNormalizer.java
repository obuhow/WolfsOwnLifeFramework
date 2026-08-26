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

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalizes any supported wolf-data 0.21 workbook into the canonical column
 * layout declared by {@link DataSyncContract}. It resolves each canonical column
 * from the source sheet by matching a list of accepted header aliases, so both
 * the exporter's own output and the legacy human-authored workbook flow through
 * the same validation and apply pipeline unchanged.
 *
 * All format-specific knowledge (aliases, defaults, value derivations) lives here
 * and nowhere else. The rest of the import pipeline only ever sees canonical
 * columns at canonical indices.
 */
public final class LegacyWorkbookNormalizer {

    private static final DataFormatter FORMATTER = new DataFormatter();

    private LegacyWorkbookNormalizer() { }

    /** Returns a new canonical-layout workbook. The source workbook is not modified. */
    public static Workbook normalize(Workbook source) {
        XSSFWorkbook target = new XSSFWorkbook();
        copyManifest(source, target);
        for (DataSyncContract.Sheet definition : DataSyncContract.manifest().sheets()) {
            Sheet src = source.getSheet(definition.name());
            Sheet dst = target.createSheet(definition.name());
            writeHeader(dst, definition.columns());
            if (src == null) {
                continue;
            }
            Map<String, Integer> srcCols = headerIndex(src);
            SheetMapper mapper = MAPPERS.get(definition.name());
            int outRow = 1;
            for (int r = 1; r <= src.getLastRowNum(); r++) {
                Row in = src.getRow(r);
                if (in == null || isBlankRow(in)) {
                    continue;
                }
                List<Map<String, String>> emitted = mapper.map(new RowView(in, srcCols), r);
                for (Map<String, String> record : emitted) {
                    Row out = dst.createRow(outRow++);
                    for (int c = 0; c < definition.columns().size(); c++) {
                        String value = record.getOrDefault(definition.columns().get(c), "");
                        out.createCell(c).setCellValue(value);
                    }
                }
            }
        }
        return target;
    }

    private static void copyManifest(Workbook source, XSSFWorkbook target) {
        Sheet src = source.getSheet("manifest");
        Sheet dst = target.createSheet("manifest");
        // Collect every key/value pair found anywhere in the source manifest,
        // including the header row, since legacy files put format/version there.
        Map<String, String> values = new LinkedHashMap<>();
        if (src != null) {
            for (int r = 0; r <= src.getLastRowNum(); r++) {
                Row in = src.getRow(r);
                if (in == null) {
                    continue;
                }
                String key = cell(in, 0);
                if (!key.isBlank()) {
                    values.putIfAbsent(key, cell(in, 1));
                }
            }
        }
        values.put("format", DataSyncContract.FORMAT);
        values.put("version", DataSyncContract.VERSION);
        // Row 0 is treated as a header by the validator, so start real key/value
        // data at row 1 and repeat format there to guarantee it is seen.
        Row header = dst.createRow(0);
        header.createCell(0).setCellValue("key");
        header.createCell(1).setCellValue("value");
        int idx = 1;
        for (Map.Entry<String, String> e : values.entrySet()) {
            Row out = dst.createRow(idx++);
            out.createCell(0).setCellValue(e.getKey());
            out.createCell(1).setCellValue(e.getValue());
        }
    }

    private static void writeHeader(Sheet dst, List<String> columns) {
        Row header = dst.createRow(0);
        for (int c = 0; c < columns.size(); c++) {
            header.createCell(c).setCellValue(columns.get(c));
        }
    }

    private static Map<String, Integer> headerIndex(Sheet sheet) {
        Map<String, Integer> map = new HashMap<>();
        Row header = sheet.getRow(0);
        if (header == null) {
            return map;
        }
        for (int c = 0; c < header.getLastCellNum(); c++) {
            String name = cell(header, c);
            if (!name.isBlank()) {
                map.putIfAbsent(name, c);
            }
        }
        return map;
    }

    private static boolean isBlankRow(Row row) {
        for (int c = 0; c < row.getLastCellNum(); c++) {
            if (!cell(row, c).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String cell(Row row, int col) {
        if (row == null) {
            return "";
        }
        Cell c = row.getCell(col);
        return c == null ? "" : FORMATTER.formatCellValue(c).trim();
    }

    /** Read-only view over a source row keyed by header name. */
    private static final class RowView {
        private final Row row;
        private final Map<String, Integer> cols;

        RowView(Row row, Map<String, Integer> cols) {
            this.row = row;
            this.cols = cols;
        }

        /** First non-empty value among the given source header aliases. */
        String get(String... aliases) {
            for (String alias : aliases) {
                Integer idx = cols.get(alias);
                if (idx != null) {
                    String value = cell(row, idx);
                    if (!value.isBlank()) {
                        return value;
                    }
                }
            }
            return "";
        }
    }

    @FunctionalInterface
    private interface SheetMapper {
        List<Map<String, String>> map(RowView row, int rowNumber);
    }

    private static Map<String, String> rec() {
        return new LinkedHashMap<>();
    }

    private static List<Map<String, String>> one(Map<String, String> record) {
        List<Map<String, String>> list = new ArrayList<>(1);
        list.add(record);
        return list;
    }

    private static String bool(String value) {
        if (value.isBlank()) {
            return "";
        }
        return String.valueOf("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value));
    }

    private static String impact(String value) {
        String v = value.trim();
        if (v.equals("1") || v.equalsIgnoreCase("POSITIVE")) {
            return "POSITIVE";
        }
        if (v.equals("-1") || v.equalsIgnoreCase("NEGATIVE")) {
            return "NEGATIVE";
        }
        if (v.equals("0") || v.equalsIgnoreCase("NEUTRAL")) {
            return "NEUTRAL";
        }
        return v;
    }

    /** Maps human-authored idea categories (RU labels or canonical names) to {@code Idea.Category}. */
    private static String ideaCategory(String value) {
        String v = value.trim();
        if (v.isEmpty()) {
            return "";
        }
        switch (v.toLowerCase()) {
            case "бизнес":
            case "business":
            case "профессиональная":
            case "professional":
                return "BUSINESS";
            case "музыкальная":
            case "music":
                return "MUSIC";
            case "личная":
            case "personal":
                return "PERSONAL";
            case "странная":
            case "creepy":
                return "CREEPY";
            default:
                return v.toUpperCase();
        }
    }

    /** Maps human-authored idea statuses (legacy or canonical) to {@code Idea.Status}. */
    private static String ideaStatus(String value) {
        String v = value.trim();
        if (v.isEmpty()) {
            return "";
        }
        switch (v.toLowerCase()) {
            case "in_bank":
            case "bank":
            case "в банке":
                return "BANK";
            case "in_work":
            case "в работе":
                return "IN_WORK";
            case "archived":
            case "в архиве":
                return "ARCHIVED";
            default:
                return v.toUpperCase();
        }
    }

    private static final List<String> WEEKDAYS =
            List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");

    private static final Map<String, SheetMapper> MAPPERS = buildMappers();

    private static Map<String, SheetMapper> buildMappers() {
        Map<String, SheetMapper> m = new HashMap<>();

        m.put("life_areas", (row, n) -> {
            Map<String, String> r = rec();
            r.put("externalId", row.get("externalId"));
            r.put("name", row.get("name"));
            r.put("sortOrder", row.get("sortOrder"));
            r.put("color", row.get("color"));
            return one(r);
        });

        m.put("life_spheres", (row, n) -> {
            Map<String, String> r = rec();
            r.put("externalId", row.get("externalId"));
            r.put("name", row.get("name"));
            r.put("sortOrder", row.get("sortOrder"));
            r.put("color", row.get("color"));
            r.put("archived", bool(row.get("archived")));
            return one(r);
        });

        m.put("projects", (row, n) -> {
            Map<String, String> r = rec();
            r.put("externalId", row.get("externalId"));
            r.put("lifeAreaExternalId", row.get("lifeAreaExternalId", "lifeAreaId"));
            r.put("parentExternalId", row.get("parentExternalId", "parentProjectId"));
            r.put("title", row.get("title", "name"));
            r.put("status", orDefault(row.get("status"), "IN_PROGRESS"));
            r.put("description", row.get("description"));
            r.put("startDate", row.get("startDate"));
            r.put("endDate", row.get("endDate"));
            r.put("totalPlanHours", row.get("totalPlanHours"));
            r.put("planDistribution", orDefault(row.get("planDistribution"), "NONE"));
            r.put("planFrozenAt", row.get("planFrozenAt"));
            return one(r);
        });

        m.put("routines", (row, n) -> {
            Map<String, String> r = rec();
            r.put("externalId", row.get("externalId"));
            r.put("title", row.get("title", "name"));
            r.put("description", row.get("description"));
            r.put("weeklyHours", orDefault(row.get("weeklyHours"), "0"));
            r.put("color", row.get("color"));
            r.put("icon", row.get("icon"));
            r.put("archived", bool(row.get("archived")));
            r.put("goalExternalIds", row.get("goalExternalIds"));
            return one(r);
        });

        m.put("routine_schedules", (row, n) -> {
            String routine = row.get("routineExternalId", "routineId");
            String day = row.get("dayOfWeek").trim().toUpperCase();
            String start = row.get("startTime");
            String end = row.get("endTime");
            String baseId = row.get("externalId");
            List<String> days;
            if (day.equals("DAILY") || day.equals("EVERYDAY") || day.equals("EVERY_DAY")) {
                days = WEEKDAYS;
            } else if (day.equals("WEEKDAYS")) {
                days = WEEKDAYS.subList(0, 5);
            } else {
                days = List.of(day);
            }
            List<Map<String, String>> out = new ArrayList<>();
            for (int i = 0; i < days.size(); i++) {
                Map<String, String> r = rec();
                String id = !baseId.isBlank()
                        ? (days.size() == 1 ? baseId : baseId + "_" + days.get(i))
                        : "sched_" + routine + "_" + n + "_" + days.get(i);
                r.put("externalId", id);
                r.put("routineExternalId", routine);
                r.put("dayOfWeek", days.get(i));
                r.put("startTime", start);
                r.put("endTime", end);
                out.add(r);
            }
            return out;
        });

        m.put("delos", (row, n) -> {
            Map<String, String> r = rec();
            r.put("externalId", row.get("externalId"));
            r.put("title", row.get("title"));
            r.put("description", row.get("description"));
            r.put("executionMode", orDefault(row.get("executionMode"), "SELF"));
            r.put("recurrenceWeekdays", row.get("recurrenceWeekdays"));
            r.put("recurrenceWindowStart", row.get("recurrenceWindowStart"));
            r.put("recurrenceWindowEnd", row.get("recurrenceWindowEnd"));
            r.put("recurrenceSlots", row.get("recurrenceSlots"));
            r.put("projectExternalIds", row.get("projectExternalIds", "projectIds"));
            r.put("primaryProjectExternalId", row.get("primaryProjectExternalId"));
            return one(r);
        });

        m.put("time_entries", (row, n) -> {
            Map<String, String> r = rec();
            r.put("externalId", row.get("externalId"));
            r.put("deloExternalId", row.get("deloExternalId", "deloId"));
            r.put("adHocText", row.get("adHocText", "adHoc"));
            r.put("startAt", row.get("startAt"));
            r.put("endAt", row.get("endAt"));
            r.put("status", orDefault(row.get("status"), "DONE"));
            return one(r);
        });

        m.put("goals", (row, n) -> {
            Map<String, String> r = rec();
            r.put("externalId", row.get("externalId"));
            r.put("title", row.get("title"));
            r.put("description", row.get("description"));
            r.put("priority", row.get("priority"));
            r.put("archived", orDefault(bool(row.get("archived")), "false"));
            r.put("projectExternalIds", row.get("projectExternalIds"));
            return one(r);
        });

        m.put("goal_metrics", (row, n) -> {
            Map<String, String> r = rec();
            r.put("externalId", row.get("externalId"));
            r.put("goalExternalId", row.get("goalExternalId", "goalId"));
            r.put("kind", row.get("kind", "metricType", "name"));
            r.put("value", orDefault(row.get("value", "currentValue"), "0"));
            r.put("targetValue", orDefault(row.get("targetValue"), "0"));
            r.put("at", orDefault(row.get("at"), "2025-06-01T00:00"));
            return one(r);
        });

        m.put("goal_week_budgets", (row, n) -> {
            Map<String, String> r = rec();
            r.put("externalId", row.get("externalId"));
            r.put("goalExternalId", row.get("goalExternalId", "goalId"));
            String isoYear = row.get("isoYear");
            String isoWeek = row.get("isoWeek");
            if (isoYear.isBlank() || isoWeek.isBlank()) {
                String period = row.get("periodId");
                String[] parts = period.split("-[Ww]");
                if (parts.length == 2) {
                    isoYear = parts[0].trim();
                    isoWeek = parts[1].trim();
                }
            }
            r.put("isoYear", isoYear);
            r.put("isoWeek", isoWeek);
            r.put("hours", row.get("hours", "plannedHours"));
            return one(r);
        });

        m.put("ideas", (row, n) -> {
            Map<String, String> r = rec();
            r.put("externalId", row.get("externalId"));
            r.put("title", row.get("title"));
            r.put("description", row.get("description"));
            r.put("category", ideaCategory(row.get("category")));
            r.put("status", ideaStatus(row.get("status")));
            r.put("promotedProjectExternalId", row.get("promotedProjectExternalId", "promotedProjectId"));
            return one(r);
        });

        m.put("notes", (row, n) -> {
            Map<String, String> r = rec();
            r.put("externalId", row.get("externalId"));
            r.put("projectExternalId", row.get("projectExternalId", "projectId"));
            r.put("deloExternalId", row.get("deloExternalId", "deloId"));
            r.put("author", orDefault(row.get("author"), "USER"));
            r.put("body", row.get("body", "text"));
            r.put("tags", row.get("tags"));
            r.put("audioRef", row.get("audioRef", "audioUrl"));
            r.put("audioContentType", row.get("audioContentType"));
            r.put("audioFilename", row.get("audioFilename"));
            return one(r);
        });

        m.put("synergies", (row, n) -> {
            Map<String, String> r = rec();
            r.put("externalId", row.get("externalId"));
            r.put("projectExternalId", row.get("projectExternalId", "projectId"));
            r.put("ideaExternalId", row.get("ideaExternalId", "ideaId"));
            r.put("routineExternalId", row.get("routineExternalId", "routineId"));
            r.put("sphereExternalId", row.get("sphereExternalId", "lifeSphereId"));
            r.put("impact", impact(row.get("impact")));
            return one(r);
        });

        m.put("project_dependencies", (row, n) -> {
            Map<String, String> r = rec();
            r.put("externalId", row.get("externalId"));
            r.put("blockerExternalId", row.get("blockerExternalId", "blockerProjectId"));
            r.put("blockedExternalId", row.get("blockedExternalId", "blockedProjectId"));
            return one(r);
        });

        m.put("backlog_items", (row, n) -> {
            Map<String, String> r = rec();
            r.put("externalId", row.get("externalId"));
            r.put("deloExternalId", row.get("deloExternalId", "deloId"));
            r.put("scope", row.get("scope").trim().toUpperCase());
            r.put("periodId", row.get("periodId"));
            r.put("plannedHours", row.get("plannedHours"));
            r.put("position", row.get("position"));
            r.put("movedToWeek", row.get("movedToWeek"));
            return one(r);
        });

        m.put("checklist_items", (row, n) -> {
            Map<String, String> r = rec();
            r.put("externalId", row.get("externalId"));
            r.put("date", row.get("date"));
            r.put("title", row.get("title"));
            r.put("deloExternalId", row.get("deloExternalId", "deloId"));
            r.put("position", row.get("position"));
            r.put("done", bool(row.get("done", "completed")));
            r.put("doneAt", row.get("doneAt"));
            return one(r);
        });

        m.put("activity_mappings", (row, n) -> {
            Map<String, String> r = rec();
            r.put("externalId", row.get("externalId"));
            r.put("activityText", row.get("activityText"));
            r.put("deloExternalId", row.get("deloExternalId", "deloId"));
            return one(r);
        });

        return m;
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
