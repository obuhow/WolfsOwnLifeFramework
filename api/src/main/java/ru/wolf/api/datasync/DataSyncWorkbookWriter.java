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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class DataSyncWorkbookWriter {
    public byte[] write(DataSyncContract.Manifest manifest, Map<String, List<Map<String, Object>>> rows)
            throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet manifestSheet = workbook.createSheet("manifest");
            row(manifestSheet, 0, List.of("key", "value"));
            row(manifestSheet, 1, List.of("format", manifest.format()));
            row(manifestSheet, 2, List.of("version", manifest.version()));
            row(manifestSheet, 3, List.of("generatedAt", Instant.now().toString()));
            int manifestRow = 4;
            for (DataSyncContract.Sheet sheet : manifest.sheets()) {
                row(manifestSheet, manifestRow++, List.of("sheet." + sheet.name(), rows.getOrDefault(sheet.name(), List.of()).size()));
            }

            for (DataSyncContract.Sheet definition : manifest.sheets()) {
                Sheet sheet = workbook.createSheet(definition.name());
                row(sheet, 0, definition.columns());
                List<Map<String, Object>> sheetRows = rows.getOrDefault(definition.name(), List.of());
                for (int i = 0; i < sheetRows.size(); i++) {
                    Map<String, Object> values = sheetRows.get(i);
                    Row target = sheet.createRow(i + 1);
                    for (int col = 0; col < definition.columns().size(); col++) {
                        Object value = values.get(definition.columns().get(col));
                        set(target.createCell(col), value);
                    }
                }
                sheet.createFreezePane(0, 1);
                sheet.autoSizeColumn(0);
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static void row(Sheet sheet, int index, List<?> values) {
        Row row = sheet.createRow(index);
        for (int i = 0; i < values.size(); i++) set(row.createCell(i), values.get(i));
    }

    private static void set(Cell cell, Object value) {
        if (value == null) cell.setBlank();
        else if (value instanceof Number number) cell.setCellValue(number.doubleValue());
        else if (value instanceof Boolean bool) cell.setCellValue(bool);
        else cell.setCellValue(String.valueOf(value));
    }
}
