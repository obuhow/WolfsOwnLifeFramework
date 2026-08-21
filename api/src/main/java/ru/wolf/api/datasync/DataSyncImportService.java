package ru.wolf.api.datasync;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.wolf.api.user.User;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DataSyncImportService {
    private final SyncImportPreviewRepository previews;
    private final SyncExternalIdRepository externalIds;
    private final ObjectMapper objectMapper;

    @Transactional
    public PreviewResponse preview(User user, MultipartFile file) throws Exception {
        byte[] bytes = file.getBytes();
        String checksum = sha256(bytes);
        List<ImportError> errors = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, ChangeSummary> plan = new LinkedHashMap<>();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            validateWorkbook(workbook, counts, errors);
            if (errors.isEmpty()) plan = buildPlan(user, workbook);
        } catch (Exception ex) {
            errors.add(new ImportError("workbook", 0, "file", null, safeMessage(ex)));
        }
        String status = errors.isEmpty() ? "VALID" : "INVALID";
        SyncImportPreview saved = previews.save(SyncImportPreview.builder()
                .user(user).checksum(checksum).workbookData(bytes).status(status)
                .summaryJson(objectMapper.writeValueAsString(counts))
                .errorsJson(objectMapper.writeValueAsString(errors))
                .planJson(objectMapper.writeValueAsString(plan)).build());
        return response(saved, counts, errors, plan);
    }

    @Transactional(readOnly = true)
    public PreviewResponse get(User user, Long id) throws Exception {
        SyncImportPreview preview = find(user, id);
        return response(preview,
                objectMapper.readValue(preview.getSummaryJson(), Map.class),
                objectMapper.readValue(preview.getErrorsJson(), objectMapper.getTypeFactory().constructCollectionType(List.class, ImportError.class)),
                objectMapper.readValue(preview.getPlanJson(), objectMapper.getTypeFactory().constructMapType(Map.class, String.class, ChangeSummary.class)));
    }

    @Transactional(readOnly = true)
    public SyncImportPreview find(User user, Long id) {
        SyncImportPreview preview = previews.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Preview не найден"));
        if (preview.getExpiresAt().isBefore(Instant.now())) throw new IllegalArgumentException("Preview истёк");
        return preview;
    }

    private Map<String, ChangeSummary> buildPlan(User user, Workbook workbook) {
        Map<String, ChangeSummary> result = new LinkedHashMap<>();
        for (DataSyncContract.Sheet definition : DataSyncContract.manifest().sheets()) {
            Set<String> seen = new HashSet<>(); int create = 0; int update = 0; int skip = 0;
            String type = entityType(definition.name());
            var sheet = workbook.getSheet(definition.name());
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                String xid = text(sheet.getRow(i), 0); if (!seen.add(xid)) continue;
                if (externalIds.findByUserAndEntityTypeAndExternalId(user, type, xid).isPresent()) update++; else create++;
            }
            int delete = 0;
            result.put(definition.name(), new ChangeSummary(create, update, skip, delete));
        }
        return result;
    }

    private void validateWorkbook(Workbook workbook, Map<String, Integer> counts, List<ImportError> errors) {
        org.apache.poi.ss.usermodel.Sheet manifest = workbook.getSheet("manifest");
        if (manifest == null) { errors.add(new ImportError("manifest", 1, null, null, "Обязательный лист manifest отсутствует")); return; }
        DataFormatter formatter = new DataFormatter(); Map<String, String> values = new LinkedHashMap<>();
        for (Row row : manifest) { if (row.getRowNum() == 0) continue; String key = text(row, 0); if (!key.isBlank()) values.put(key, text(row, 1)); }
        if (!DataSyncContract.FORMAT.equals(values.get("format"))) errors.add(new ImportError("manifest", 0, "format", null, "Ожидался format=wolf-data"));
        if (!DataSyncContract.VERSION.equals(values.get("version"))) errors.add(new ImportError("manifest", 0, "version", null, "Поддерживается только version=0.21"));
        Map<String, Set<String>> idsBySheet = new LinkedHashMap<>();
        for (DataSyncContract.Sheet definition : DataSyncContract.manifest().sheets()) {
            var sheet = workbook.getSheet(definition.name());
            if (sheet == null) { errors.add(new ImportError(definition.name(), 1, null, null, "Лист отсутствует")); continue; }
            Row header = sheet.getRow(0); boolean headerMatches = header != null && header.getLastCellNum() == definition.columns().size();
            if (headerMatches) for (int i = 0; i < definition.columns().size(); i++) if (!definition.columns().get(i).equals(text(header, i))) { headerMatches = false; break; }
            if (!headerMatches) errors.add(new ImportError(definition.name(), 1, null, null, "Заголовок листа не соответствует контракту"));
            counts.put(definition.name(), Math.max(0, sheet.getLastRowNum())); Set<String> ids = new HashSet<>(); idsBySheet.put(definition.name(), ids);
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex); String xid = text(row, 0);
                if (xid.isBlank()) errors.add(new ImportError(definition.name(), rowIndex + 1, "externalId", null, "externalId обязателен"));
                else if (!ids.add(xid)) errors.add(new ImportError(definition.name(), rowIndex + 1, "externalId", xid, "externalId повторяется в листе"));
            }
        }
        validateReferences(workbook, formatter, idsBySheet, errors);
        validateTypes(workbook, errors);
    }

    private void validateTypes(Workbook workbook, List<ImportError> errors) {
        validateSheetRows(workbook, "projects", 6, errors, ValueType.DATE); validateSheetRows(workbook, "projects", 7, errors, ValueType.DATE); validateSheetRows(workbook, "projects", 8, errors, ValueType.DECIMAL); validateSheetRows(workbook, "projects", 10, errors, ValueType.DATE);
        validateSheetRows(workbook, "routines", 3, errors, ValueType.DECIMAL); validateSheetRows(workbook, "routine_schedules", 3, errors, ValueType.TIME); validateSheetRows(workbook, "routine_schedules", 4, errors, ValueType.TIME);
        validateSheetRows(workbook, "delos", 5, errors, ValueType.TIME); validateSheetRows(workbook, "delos", 6, errors, ValueType.TIME);
        validateSheetRows(workbook, "time_entries", 3, errors, ValueType.DATETIME); validateSheetRows(workbook, "time_entries", 4, errors, ValueType.DATETIME);
        validateSheetRows(workbook, "goal_metrics", 3, errors, ValueType.DECIMAL); validateSheetRows(workbook, "goal_metrics", 4, errors, ValueType.DECIMAL); validateSheetRows(workbook, "goal_metrics", 5, errors, ValueType.DATETIME);
        validateSheetRows(workbook, "goal_week_budgets", 4, errors, ValueType.DECIMAL); validateSheetRows(workbook, "checklist_items", 1, errors, ValueType.DATE);
        validateEnum(workbook, "projects", 4, ProjectEnums.STATUS, errors); validateEnum(workbook, "projects", 9, ProjectEnums.PLAN, errors); validateEnum(workbook, "delos", 3, ProjectEnums.EXECUTION, errors); validateEnum(workbook, "time_entries", 5, ProjectEnums.TIME_STATUS, errors);
        validateEnum(workbook, "routine_schedules", 2, ProjectEnums.DAY_OF_WEEK, errors); validateEnum(workbook, "backlog_items", 2, ProjectEnums.BACKLOG_SCOPE, errors); validateEnum(workbook, "notes", 3, ProjectEnums.NOTE_AUTHOR, errors); validateEnum(workbook, "synergies", 5, ProjectEnums.IMPACT, errors);
        validateRequiredAndScalarFields(workbook, errors);
        validateIntervals(workbook, errors);
        validateProjectCycles(workbook, errors);
    }

    private void validateRequiredAndScalarFields(Workbook workbook, List<ImportError> errors) {
        requireColumns(workbook, "life_areas", List.of(1), errors); requireColumns(workbook, "life_spheres", List.of(1), errors);
        requireColumns(workbook, "projects", List.of(1, 3, 4, 9), errors); requireColumns(workbook, "routines", List.of(1, 3), errors);
        requireColumns(workbook, "routine_schedules", List.of(1, 2, 3, 4), errors); requireColumns(workbook, "delos", List.of(1, 3), errors);
        requireColumns(workbook, "time_entries", List.of(3, 4, 5), errors); requireColumns(workbook, "goals", List.of(1, 3), errors);
        requireColumns(workbook, "goal_metrics", List.of(1, 2, 3, 5), errors); requireColumns(workbook, "goal_week_budgets", List.of(1, 2, 3, 4), errors);
        requireColumns(workbook, "ideas", List.of(1, 3, 4), errors); requireColumns(workbook, "notes", List.of(3, 4), errors);
        requireColumns(workbook, "synergies", List.of(4, 5), errors); requireColumns(workbook, "project_dependencies", List.of(1, 2), errors);
        requireColumns(workbook, "backlog_items", List.of(1, 2, 3), errors); requireColumns(workbook, "checklist_items", List.of(1, 2), errors); requireColumns(workbook, "activity_mappings", List.of(1, 2), errors);
        validateInteger(workbook, "life_areas", 2, errors); validateInteger(workbook, "life_spheres", 2, errors); validateInteger(workbook, "goals", 3, errors);
        validateInteger(workbook, "goal_week_budgets", 2, errors); validateInteger(workbook, "goal_week_budgets", 3, errors); validateInteger(workbook, "backlog_items", 5, errors); validateInteger(workbook, "checklist_items", 4, errors);
        validateBoolean(workbook, "life_spheres", 4, errors); validateBoolean(workbook, "routines", 6, errors); validateBoolean(workbook, "goals", 4, errors); validateBoolean(workbook, "checklist_items", 5, errors);
        validateIsoWeek(workbook, errors); validateScheduleIntervals(workbook, errors); validateDoneAt(workbook, errors);
    }

    private void requireColumns(Workbook workbook, String sheetName, List<Integer> columns, List<ImportError> errors) { var sheet = workbook.getSheet(sheetName); if (sheet == null) return; for (int i = 1; i <= sheet.getLastRowNum(); i++) for (Integer col : columns) if (text(sheet.getRow(i), col).isBlank()) errors.add(new ImportError(sheetName, i + 1, null, text(sheet.getRow(i), 0), "Обязательное поле не заполнено: " + col)); }
    private void validateInteger(Workbook workbook, String sheetName, int col, List<ImportError> errors) { var sheet = workbook.getSheet(sheetName); if (sheet == null) return; for (int i = 1; i <= sheet.getLastRowNum(); i++) { String value = text(sheet.getRow(i), col); if (!value.isBlank()) try { Integer.parseInt(value); } catch (RuntimeException ex) { errors.add(new ImportError(sheetName, i + 1, null, text(sheet.getRow(i), 0), "Ожидалось целое число: " + value)); } } }
    private void validateBoolean(Workbook workbook, String sheetName, int col, List<ImportError> errors) { var sheet = workbook.getSheet(sheetName); if (sheet == null) return; for (int i = 1; i <= sheet.getLastRowNum(); i++) { String value = text(sheet.getRow(i), col); if (!value.isBlank() && !Set.of("true", "false").contains(value.toLowerCase())) errors.add(new ImportError(sheetName, i + 1, null, text(sheet.getRow(i), 0), "Ожидалось true/false: " + value)); } }
    private void validateIsoWeek(Workbook workbook, List<ImportError> errors) { var sheet = workbook.getSheet("goal_week_budgets"); if (sheet == null) return; for (int i = 1; i <= sheet.getLastRowNum(); i++) { try { int year = Integer.parseInt(text(sheet.getRow(i), 2)); int week = Integer.parseInt(text(sheet.getRow(i), 3)); if (year < 1 || week < 1 || week > 53) throw new IllegalArgumentException(); } catch (RuntimeException ex) { errors.add(new ImportError("goal_week_budgets", i + 1, "isoYear/isoWeek", text(sheet.getRow(i), 0), "Некорректная ISO-неделя")); } } }
    private void validateScheduleIntervals(Workbook workbook, List<ImportError> errors) { var sheet = workbook.getSheet("routine_schedules"); if (sheet == null) return; for (int i = 1; i <= sheet.getLastRowNum(); i++) try { if (!LocalTime.parse(text(sheet.getRow(i), 3)).isBefore(LocalTime.parse(text(sheet.getRow(i), 4)))) errors.add(new ImportError("routine_schedules", i + 1, "startTime/endTime", text(sheet.getRow(i), 0), "endTime должен быть позже startTime")); } catch (RuntimeException ignored) { } }
    private void validateDoneAt(Workbook workbook, List<ImportError> errors) { var sheet = workbook.getSheet("checklist_items"); if (sheet == null) return; for (int i = 1; i <= sheet.getLastRowNum(); i++) { String done = text(sheet.getRow(i), 5), doneAt = text(sheet.getRow(i), 6); if ("true".equalsIgnoreCase(done) && doneAt.isBlank()) errors.add(new ImportError("checklist_items", i + 1, "doneAt", text(sheet.getRow(i), 0), "doneAt обязателен при done=true")); if ("false".equalsIgnoreCase(done) && !doneAt.isBlank()) errors.add(new ImportError("checklist_items", i + 1, "doneAt", text(sheet.getRow(i), 0), "doneAt должен быть пустым при done=false")); if (!doneAt.isBlank()) try { Instant.parse(doneAt); } catch (RuntimeException ex) { errors.add(new ImportError("checklist_items", i + 1, "doneAt", text(sheet.getRow(i), 0), "Некорректный Instant")); } } }
    private void validateProjectCycles(Workbook workbook, List<ImportError> errors) { var sheet = workbook.getSheet("projects"); if (sheet == null) return; Map<String, String> parents = new HashMap<>(); for (int i = 1; i <= sheet.getLastRowNum(); i++) parents.put(text(sheet.getRow(i), 0), text(sheet.getRow(i), 2)); for (String id : parents.keySet()) { Set<String> seen = new HashSet<>(); String current = id; while (!current.isBlank() && parents.containsKey(current)) { if (!seen.add(current)) { errors.add(new ImportError("projects", 0, "parentExternalId", id, "Обнаружен цикл родителей проекта")); break; } current = parents.get(current); } } }

    private void validateSheetRows(Workbook workbook, String sheetName, int col, List<ImportError> errors, ValueType type) {
        var sheet = workbook.getSheet(sheetName); if (sheet == null) return;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) { String value = text(sheet.getRow(i), col); if (value.isBlank()) continue; try { switch (type) { case DATE -> LocalDate.parse(value); case DATETIME -> LocalDateTime.parse(value); case TIME -> LocalTime.parse(value); case DECIMAL -> new java.math.BigDecimal(value); } } catch (RuntimeException e) { errors.add(new ImportError(sheetName, i + 1, null, text(sheet.getRow(i), 0), "Некорректное значение: " + value)); } }
    }

    private void validateEnum(Workbook workbook, String sheetName, int col, Set<String> allowed, List<ImportError> errors) { var sheet = workbook.getSheet(sheetName); if (sheet == null) return; for (int i = 1; i <= sheet.getLastRowNum(); i++) { String value = text(sheet.getRow(i), col); if (!value.isBlank() && !allowed.contains(value)) errors.add(new ImportError(sheetName, i + 1, null, text(sheet.getRow(i), 0), "Недопустимое enum-значение: " + value)); } }

    private void validateIntervals(Workbook workbook, List<ImportError> errors) {
        var sheet = workbook.getSheet("time_entries"); if (sheet == null) return;
        List<LocalDateTime[]> intervals = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            String startText = text(sheet.getRow(i), 3), endText = text(sheet.getRow(i), 4); if (startText.isBlank() || endText.isBlank()) continue;
            try {
                LocalDateTime start = LocalDateTime.parse(startText), end = LocalDateTime.parse(endText);
                if (!start.isBefore(end)) errors.add(new ImportError("time_entries", i + 1, "endAt", text(sheet.getRow(i), 0), "endAt должен быть позже startAt"));
                if (start.getMinute() % 15 != 0 || end.getMinute() % 15 != 0 || start.getSecond() != 0 || end.getSecond() != 0) errors.add(new ImportError("time_entries", i + 1, "startAt/endAt", text(sheet.getRow(i), 0), "Интервал должен быть выровнен по 15 минут"));
                for (LocalDateTime[] previous : intervals) if (start.isBefore(previous[1]) && previous[0].isBefore(end)) errors.add(new ImportError("time_entries", i + 1, "startAt/endAt", text(sheet.getRow(i), 0), "Интервал пересекается с другой записью в workbook"));
                intervals.add(new LocalDateTime[]{start, end});
            } catch (RuntimeException ignored) { }
        }
    }

    private void validateReferences(Workbook workbook, DataFormatter formatter, Map<String, Set<String>> ids, List<ImportError> errors) {
        ref(workbook, ids, "projects", 1, "life_areas", errors, false); ref(workbook, ids, "projects", 2, "projects", errors, true); ref(workbook, ids, "routine_schedules", 1, "routines", errors, false); ref(workbook, ids, "time_entries", 1, "delos", errors, true); ref(workbook, ids, "goal_metrics", 1, "goals", errors, false); ref(workbook, ids, "goal_week_budgets", 1, "goals", errors, false); ref(workbook, ids, "ideas", 6, "projects", errors, true); ref(workbook, ids, "notes", 1, "projects", errors, true); ref(workbook, ids, "notes", 2, "delos", errors, true); ref(workbook, ids, "synergies", 1, "projects", errors, true); ref(workbook, ids, "synergies", 3, "routines", errors, true); ref(workbook, ids, "synergies", 4, "life_spheres", errors, false); ref(workbook, ids, "project_dependencies", 1, "projects", errors, false); ref(workbook, ids, "project_dependencies", 2, "projects", errors, false); ref(workbook, ids, "backlog_items", 1, "delos", errors, false); ref(workbook, ids, "checklist_items", 3, "delos", errors, true); ref(workbook, ids, "activity_mappings", 2, "delos", errors, false); refPipe(workbook, ids, "delos", 9, "projects", errors, true);
        var delos = workbook.getSheet("delos");
        if (delos != null) for (int i = 1; i <= delos.getLastRowNum(); i++) { String projects = text(delos.getRow(i), 9), primary = text(delos.getRow(i), 10); Set<String> links = new HashSet<>(); for (String item : projects.split("\\|")) if (!item.isBlank()) links.add(item.trim()); if (!primary.isBlank() && !links.contains(primary)) errors.add(new ImportError("delos", i + 1, "primaryProjectExternalId", text(delos.getRow(i), 0), "primaryProjectExternalId должен входить в projectExternalIds")); }
    }

    private void ref(Workbook workbook, Map<String, Set<String>> ids, String sheetName, int col, String target, List<ImportError> errors, boolean optional) { var sheet = workbook.getSheet(sheetName); if (sheet == null) return; Set<String> targets = ids.getOrDefault(target, Set.of()); for (int i = 1; i <= sheet.getLastRowNum(); i++) { String value = text(sheet.getRow(i), col); if (value.isBlank() && optional) continue; if (!value.isBlank() && !targets.contains(value)) errors.add(new ImportError(sheetName, i + 1, null, text(sheet.getRow(i), 0), "Неизвестная ссылка: " + value)); } }
    private void refPipe(Workbook workbook, Map<String, Set<String>> ids, String sheetName, int col, String target, List<ImportError> errors, boolean optional) { var sheet = workbook.getSheet(sheetName); if (sheet == null) return; Set<String> targets = ids.getOrDefault(target, Set.of()); for (int i = 1; i <= sheet.getLastRowNum(); i++) { String value = text(sheet.getRow(i), col); if (value.isBlank() && optional) continue; for (String item : value.split("\\|")) { String ref = item.trim(); if (!ref.isBlank() && !targets.contains(ref)) errors.add(new ImportError(sheetName, i + 1, null, text(sheet.getRow(i), 0), "Неизвестная ссылка: " + ref)); } } }

    private PreviewResponse response(SyncImportPreview preview, Map<String, Integer> counts, List<ImportError> errors, Map<String, ChangeSummary> plan) { return new PreviewResponse(preview.getId(), preview.getChecksum(), preview.getStatus(), counts, errors, plan, errors.isEmpty(), preview.getExpiresAt()); }
    private static String text(Row row, int col) { return row == null || row.getCell(col) == null ? "" : new DataFormatter().formatCellValue(row.getCell(col)).trim(); }
    private static String sha256(byte[] bytes) throws Exception { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static String safeMessage(Exception ex) { return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(); }
    private static String entityType(String sheet) { return switch (sheet) { case "life_areas" -> "life_area"; case "life_spheres" -> "life_sphere"; case "routine_schedules" -> "routine_schedule"; case "time_entries" -> "time_entry"; case "goal_metrics" -> "goal_metric"; case "goal_week_budgets" -> "goal_week_budget"; case "project_dependencies" -> "project_dependency"; case "backlog_items" -> "backlog_item"; case "checklist_items" -> "checklist_item"; case "activity_mappings" -> "activity_mapping"; case "delos" -> "delo"; case "notes" -> "note"; default -> sheet.substring(0, sheet.length() - 1); }; }
    private enum ValueType { DATE, DATETIME, TIME, DECIMAL }
    private static final class ProjectEnums { static final Set<String> STATUS = Set.of("IN_PROGRESS", "ARCHIVED"); static final Set<String> PLAN = Set.of("NONE", "EVEN_ALL_DAYS", "EVEN_WEEKDAYS"); static final Set<String> EXECUTION = Set.of("SELF", "DELEGATABLE", "AUTOMATABLE"); static final Set<String> TIME_STATUS = Set.of("PLANNED", "DONE", "UNKNOWN"); static final Set<String> DAY_OF_WEEK = Set.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"); static final Set<String> BACKLOG_SCOPE = Set.of("WEEK", "MONTH"); static final Set<String> NOTE_AUTHOR = Set.of("USER", "AGENT"); static final Set<String> IMPACT = Set.of("POSITIVE", "NEGATIVE", "NEUTRAL"); }

    public record ImportError(String sheet, int row, String field, String externalId, String message) { }
    public record ChangeSummary(int create, int update, int skip, int delete) { }
    public record PreviewResponse(Long id, String checksum, String status, Map<String, Integer> counts, List<ImportError> errors, Map<String, ChangeSummary> plan, boolean applyAllowed, Instant expiresAt) { }
}
