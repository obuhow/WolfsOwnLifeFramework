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
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Round-trip CSV codec for the data-sync contract (релиз 1.0, тикет 09).
 *
 * <p>Экспорт и импорт CSV повторяют тот же контракт {@link DataSyncContract}, что и
 * xlsx-workbook, поэтому один CSV-файл несёт ВСЕ листы: каждая таблица предваряется
 * строкой-маркером {@code # sheet:<name>}, затем строка заголовков (имена колонок
 * контракта) и строки данных. Первым идёт блок {@code # sheet:manifest} с format/version.
 *
 * <p>CSV импортируется тем же пайплайном, что и xlsx: {@link #toWorkbook(byte[])}
 * конвертирует CSV обратно в {@link XSSFWorkbook}, дальше работает существующая
 * валидация и {@link DataSyncImportApplyService} (upsert по externalId).
 *
 * <p>Экранирование — RFC 4180: поля с {@code , " \n \r} оборачиваются в кавычки,
 * внутренние кавычки удваиваются.
 */
@Component
public class DataSyncCsvCodec {

    private static final String SHEET_MARKER = "# sheet:";

    /** Сериализует построчное представление контракта в единый CSV (UTF-8). */
    public byte[] toCsv(DataSyncContract.Manifest manifest, Map<String, List<Map<String, Object>>> rows) {
        StringBuilder out = new StringBuilder();

        out.append(SHEET_MARKER).append("manifest").append('\n');
        out.append("key,value").append('\n');
        out.append(escapeRow(List.of("format", manifest.format()))).append('\n');
        out.append(escapeRow(List.of("version", manifest.version()))).append('\n');
        out.append(escapeRow(List.of("generatedAt", Instant.now().toString()))).append('\n');

        for (DataSyncContract.Sheet definition : manifest.sheets()) {
            out.append(SHEET_MARKER).append(definition.name()).append('\n');
            out.append(escapeRow(definition.columns())).append('\n');
            List<Map<String, Object>> sheetRows = rows.getOrDefault(definition.name(), List.of());
            for (Map<String, Object> values : sheetRows) {
                List<String> cells = new ArrayList<>(definition.columns().size());
                for (String column : definition.columns()) {
                    cells.add(stringify(values.get(column)));
                }
                out.append(escapeRow(cells)).append('\n');
            }
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Конвертирует CSV-набор (с маркерами {@code # sheet:<name>}) обратно в xlsx-workbook
     * канонического формата, который принимает существующий импорт-пайплайн.
     */
    public byte[] toWorkbook(byte[] csvBytes) throws Exception {
        String text = new String(csvBytes, StandardCharsets.UTF_8);
        List<String> lines = splitLines(text);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            String currentSheet = null;
            Sheet sheet = null;
            int rowIndex = 0;
            for (String line : lines) {
                if (line.startsWith(SHEET_MARKER)) {
                    currentSheet = line.substring(SHEET_MARKER.length()).trim();
                    sheet = workbook.createSheet(currentSheet);
                    rowIndex = 0;
                    continue;
                }
                if (sheet == null) {
                    continue; // до первого маркера ничего осмысленного нет
                }
                // Пустую хвостовую строку игнорируем, но не внутри данных с запятыми.
                if (line.isEmpty() && rowIndex > 0) {
                    continue;
                }
                List<String> cells = parseCsvLine(line);
                Row row = sheet.createRow(rowIndex++);
                for (int col = 0; col < cells.size(); col++) {
                    Cell cell = row.createCell(col);
                    cell.setCellValue(cells.get(col));
                }
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            return output.toByteArray();
        }
    }

    /** Verifies the payload is our CSV shape (starts with a sheet marker). */
    public boolean looksLikeCsv(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return false;
        String head = new String(bytes, 0, Math.min(bytes.length, 32), StandardCharsets.UTF_8);
        return head.startsWith(SHEET_MARKER);
    }

    private static String stringify(Object value) {
        if (value == null) return "";
        if (value instanceof Boolean bool) return bool ? "true" : "false";
        return String.valueOf(value);
    }

    private static String escapeRow(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(escapeCell(cells.get(i)));
        }
        return sb.toString();
    }

    private static String escapeCell(String value) {
        if (value == null) return "";
        boolean needsQuoting = value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r");
        if (!needsQuoting) return value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /** Splits on record boundaries, honouring quoted fields that contain newlines. */
    private static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                current.append(c);
            } else if ((c == '\n' || c == '\r') && !inQuotes) {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++; // CRLF → один разрыв
                }
                lines.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    /** Parses one CSV record into fields (RFC 4180 quoting). */
    private static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(field.toString());
                    field.setLength(0);
                } else {
                    field.append(c);
                }
            }
        }
        fields.add(field.toString());
        return fields;
    }
}
