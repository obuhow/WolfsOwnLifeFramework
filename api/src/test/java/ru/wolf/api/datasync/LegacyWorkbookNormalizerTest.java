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

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the real acceptance workbook normalizes into the canonical
 * contract layout. This is a fast local oracle used while wiring the legacy
 * normalizer; it does not touch the database.
 */
class LegacyWorkbookNormalizerTest {

    private static final Path ACCEPTANCE =
            Path.of(System.getProperty("user.dir")).getParent().getParent()
                    .resolve("docs/import/wolf-data-v0.21-TEST-filled.xlsx");

    @Test
    void acceptanceFileNormalizesToCanonicalLayout() throws Exception {
        if (!Files.exists(ACCEPTANCE)) {
            return; // acceptance file only present in the developer worktree
        }
        byte[] bytes = Files.readAllBytes(ACCEPTANCE);
        byte[] canonical;
        try (Workbook source = new XSSFWorkbook(new ByteArrayInputStream(bytes));
             Workbook normalized = LegacyWorkbookNormalizer.normalize(source);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            normalized.write(out);
            canonical = out.toByteArray();
        }
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(canonical))) {
            for (DataSyncContract.Sheet definition : DataSyncContract.manifest().sheets()) {
                var sheet = wb.getSheet(definition.name());
                assertThat(sheet).as("sheet %s present", definition.name()).isNotNull();
                var header = sheet.getRow(0);
                assertThat(header.getLastCellNum())
                        .as("header width for %s", definition.name())
                        .isEqualTo((short) definition.columns().size());
                for (int c = 0; c < definition.columns().size(); c++) {
                    assertThat(header.getCell(c).getStringCellValue())
                            .as("header %s col %d", definition.name(), c)
                            .isEqualTo(definition.columns().get(c));
                }
            }
        }
    }
}
