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

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import static org.assertj.core.api.Assertions.assertThat;

class DataSyncValidationTest {
    @Test
    void workbook_writer_emits_all_contract_sheets_even_when_empty() throws Exception {
        byte[] bytes = new DataSyncWorkbookWriter().write(DataSyncContract.manifest(), java.util.Map.of());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(DataSyncContract.manifest().sheets().size() + 1);
            assertThat(workbook.getSheet("manifest")).isNotNull();
            assertThat(workbook.getSheet("time_entries").getRow(0).getCell(0).getStringCellValue()).isEqualTo("externalId");
        }
    }

    @Test
    void contract_keeps_promoted_project_after_status_column() {
        assertThat(DataSyncContract.manifest().sheets().stream()
                .filter(sheet -> sheet.name().equals("ideas"))
                .findFirst().orElseThrow().columns())
                .containsExactly("externalId", "title", "description", "category", "status", "promotedProjectExternalId");
    }

    @Test
    void contract_uses_zero_based_column_positions_for_project_dates() {
        assertThat(DataSyncContract.manifest().sheets().stream()
                .filter(sheet -> sheet.name().equals("projects"))
                .findFirst().orElseThrow().columns())
                .containsExactly("externalId", "lifeAreaExternalId", "parentExternalId", "title", "status", "description", "startDate", "endDate", "totalPlanHours", "planDistribution", "planFrozenAt");
    }
}
