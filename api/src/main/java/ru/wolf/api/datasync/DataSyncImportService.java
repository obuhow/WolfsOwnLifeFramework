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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DataSyncImportService {
    private final SyncImportPreviewRepository previews;
    private final ObjectMapper objectMapper;

    @Transactional
    public PreviewResponse preview(User user, MultipartFile file) throws Exception {
        byte[] bytes = file.getBytes();
        String checksum = sha256(bytes);
        List<ImportError> errors = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            validateWorkbook(workbook, counts, errors);
        } catch (Exception ex) {
            errors.add(new ImportError("workbook", 0, "file", null, ex.getMessage()));
        }
        String status = errors.isEmpty() ? "VALID" : "INVALID";
        SyncImportPreview saved = previews.save(SyncImportPreview.builder()
                .user(user).checksum(checksum).workbookData(bytes).status(status)
                .summaryJson(objectMapper.writeValueAsString(counts))
                .errorsJson(objectMapper.writeValueAsString(errors)).build());
        return response(saved, counts, errors);
    }

    @Transactional(readOnly = true)
    public PreviewResponse get(User user, Long id) throws Exception {
        SyncImportPreview preview = previews.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Preview не найден"));
        if (preview.getExpiresAt().isBefore(Instant.now())) throw new IllegalArgumentException("Preview истёк");
        return response(preview,
                objectMapper.readValue(preview.getSummaryJson(), Map.class),
                objectMapper.readValue(preview.getErrorsJson(), objectMapper.getTypeFactory().constructCollectionType(List.class, ImportError.class)));
    }

    @Transactional(readOnly = true)
    public SyncImportPreview find(User user, Long id) {
        SyncImportPreview preview = previews.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Preview не найден"));
        if (preview.getExpiresAt().isBefore(Instant.now())) throw new IllegalArgumentException("Preview истёк");
        return preview;
    }

    private void validateWorkbook(Workbook workbook, Map<String, Integer> counts, List<ImportError> errors) {
        org.apache.poi.ss.usermodel.Sheet manifest = workbook.getSheet("manifest");
        if (manifest == null) {
            errors.add(new ImportError("manifest", 1, null, null, "Обязательный лист manifest отсутствует"));
            return;
        }
        Map<String, String> values = new LinkedHashMap<>();
        DataFormatter formatter = new DataFormatter();
        for (Row row : manifest) {
            if (row.getRowNum() == 0) continue;
            String key = formatter.formatCellValue(row.getCell(0));
            if (!key.isBlank()) values.put(key, formatter.formatCellValue(row.getCell(1)));
        }
        if (!DataSyncContract.FORMAT.equals(values.get("format"))) errors.add(new ImportError("manifest", 0, "format", null, "Ожидался format=wolf-data"));
        if (!DataSyncContract.VERSION.equals(values.get("version"))) errors.add(new ImportError("manifest", 0, "version", null, "Поддерживается только version=0.21"));
        Map<String, java.util.Set<String>> idsBySheet = new LinkedHashMap<>();
        for (DataSyncContract.Sheet definition : DataSyncContract.manifest().sheets()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheet(definition.name());
            if (sheet == null) {
                errors.add(new ImportError(definition.name(), 1, null, null, "Лист отсутствует"));
                continue;
            }
            Row header = sheet.getRow(0);
            boolean headerMatches = header != null && header.getLastCellNum() == definition.columns().size();
            if (headerMatches) {
                for (int i = 0; i < definition.columns().size(); i++) {
                    if (!definition.columns().get(i).equals(formatter.formatCellValue(header.getCell(i)))) {
                        headerMatches = false;
                        break;
                    }
                }
            }
            if (!headerMatches) errors.add(new ImportError(definition.name(), 1, null, null, "Заголовок листа не соответствует контракту"));
            counts.put(definition.name(), Math.max(0, sheet.getLastRowNum()));
            java.util.Set<String> ids = new java.util.HashSet<>();
            idsBySheet.put(definition.name(), ids);
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                String externalId = row == null ? "" : formatter.formatCellValue(row.getCell(0));
                if (externalId.isBlank()) {
                    errors.add(new ImportError(definition.name(), rowIndex + 1, "externalId", null, "externalId обязателен"));
                } else if (!ids.add(externalId)) {
                    errors.add(new ImportError(definition.name(), rowIndex + 1, "externalId", externalId, "externalId повторяется в листе"));
                }
            }
        }
        validateReferences(workbook, formatter, idsBySheet, errors);
    }

    private void validateReferences(Workbook workbook, DataFormatter formatter,
                                    Map<String, java.util.Set<String>> idsBySheet, List<ImportError> errors) {
        validateReferenceColumn(workbook, formatter, idsBySheet, "projects", 1, "life_areas", errors, false);
        validateReferenceColumn(workbook, formatter, idsBySheet, "projects", 2, "projects", errors, true);
        validateReferenceColumn(workbook, formatter, idsBySheet, "routine_schedules", 1, "routines", errors, false);
        validateReferenceColumn(workbook, formatter, idsBySheet, "time_entries", 1, "delos", errors, true);
        validateReferenceColumn(workbook, formatter, idsBySheet, "backlog_items", 1, "delos", errors, false);
        validateReferenceColumn(workbook, formatter, idsBySheet, "activity_mappings", 2, "delos", errors, false);
        validateReferenceColumn(workbook, formatter, idsBySheet, "checklist_items", 4, "delos", errors, true);
        validateReferenceColumn(workbook, formatter, idsBySheet, "notes", 1, "projects", errors, true);
        validateReferenceColumn(workbook, formatter, idsBySheet, "notes", 2, "delos", errors, true);
        validateReferenceColumn(workbook, formatter, idsBySheet, "ideas", 6, "projects", errors, true);
        validateReferenceColumn(workbook, formatter, idsBySheet, "goal_metrics", 1, "goals", errors, false);
        validateReferenceColumn(workbook, formatter, idsBySheet, "goal_week_budgets", 1, "goals", errors, false);
        validateReferenceColumn(workbook, formatter, idsBySheet, "synergies", 5, "life_spheres", errors, false);
        validateReferenceColumn(workbook, formatter, idsBySheet, "project_dependencies", 1, "projects", errors, false);
        validateReferenceColumn(workbook, formatter, idsBySheet, "project_dependencies", 2, "projects", errors, false);
    }

    private void validateReferenceColumn(Workbook workbook, DataFormatter formatter,
                                         Map<String, java.util.Set<String>> idsBySheet, String sheetName, int column,
                                         String targetSheet, List<ImportError> errors, boolean optional) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) return;
        java.util.Set<String> targets = idsBySheet.getOrDefault(targetSheet, java.util.Set.of());
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex); String value = formatter.formatCellValue(row.getCell(column));
            if (value.isBlank() && optional) continue;
            if (!value.isBlank() && !targets.contains(value)) {
                errors.add(new ImportError(sheetName, rowIndex + 1, null, formatter.formatCellValue(row.getCell(0)), "Неизвестная ссылка: " + value));
            }
        }
    }

    private PreviewResponse response(SyncImportPreview preview, Map<String, Integer> counts, List<ImportError> errors) {
        return new PreviewResponse(preview.getId(), preview.getChecksum(), preview.getStatus(), counts, errors,
                errors.isEmpty(), preview.getExpiresAt());
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    public record ImportError(String sheet, int row, String field, String externalId, String message) { }
    public record PreviewResponse(Long id, String checksum, String status, Map<String, Integer> counts,
                                  List<ImportError> errors, boolean applyAllowed, Instant expiresAt) { }
}
