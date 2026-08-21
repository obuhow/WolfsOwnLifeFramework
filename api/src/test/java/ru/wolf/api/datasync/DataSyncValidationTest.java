package ru.wolf.api.datasync;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;

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
    void contract_uses_zero_based_column_positions_for_project_dates() {
        assertThat(DataSyncContract.manifest().sheets().stream()
                .filter(sheet -> sheet.name().equals("projects"))
                .findFirst().orElseThrow().columns())
                .containsExactly("externalId", "lifeAreaExternalId", "parentExternalId", "title", "status", "description", "startDate", "endDate", "totalPlanHours", "planDistribution", "planFrozenAt");
    }
}
