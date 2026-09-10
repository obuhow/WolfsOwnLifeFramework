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
package ru.wolf.api.importxlsx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Release 1.4 ticket 01 — DB-free tests for the weekly-grid parser.
 *
 * <p>The owner's real {@code Расписание.xlsx} is deliberately NOT in the repository (personal data;
 * see {@code .scratch/xlsx-schedule-import/.gitignore}), so these tests build a synthetic workbook
 * in the SAME format — date serials in row 1 columns D–J, day captions in row 2, time fractions in
 * column C, activities in D–J, and a «Факт» aggregate block at the bottom. The real file was used
 * once, out of band, to verify the parser reproduces the spike's cell/activity totals.
 */
class XlsxScheduleGridParserTest {

    private final XlsxScheduleGridParser parser = new XlsxScheduleGridParser();

    /** Excel serial for 2026-06-01 (Monday) — the first date of the owner's real first sheet. */
    private static final int SERIAL_2026_06_01 = 46174;

    private static double timeFraction(int hour, int minute) {
        return (hour * 60 + minute) / (24.0 * 60.0);
    }

    /**
     * Builds a sheet in the owner's format.
     *
     * @param firstSerial date serial for column D; the following six columns get consecutive days
     * @param slots       {hour, minute} pairs, one slot row each
     * @param activities  activities[slotIndex][dayIndex]; null leaves the cell empty
     */
    private byte[] workbook(String sheetName, int firstSerial, int[][] slots, String[][] activities,
                            boolean withFactBlock) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName);

            Row dateRow = sheet.createRow(0);
            for (int day = 0; day < 7; day++) {
                dateRow.createCell(3 + day).setCellValue(firstSerial + day);
            }
            Row captionRow = sheet.createRow(1);
            String[] captions = {"Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"};
            for (int day = 0; day < 7; day++) {
                captionRow.createCell(3 + day).setCellValue(captions[day]);
            }

            for (int s = 0; s < slots.length; s++) {
                Row row = sheet.createRow(2 + s);
                row.createCell(2).setCellValue(timeFraction(slots[s][0], slots[s][1]));
                for (int day = 0; day < 7; day++) {
                    String activity = activities[s][day];
                    if (activity != null) {
                        row.createCell(3 + day).setCellValue(activity);
                    }
                }
            }

            if (withFactBlock) {
                // The «Факт» block: a caption row and aggregate hours. Column C holds whole numbers
                // (hours), not day fractions — that is what must keep these rows out of the import.
                Row spacer = sheet.createRow(2 + slots.length + 1);
                spacer.createCell(1).setCellValue("Факт");
                Row fact = sheet.createRow(2 + slots.length + 2);
                fact.createCell(1).setCellValue("Java");
                fact.createCell(2).setCellValue(42);
                fact.createCell(3).setCellValue("НЕ ДОЛЖНО ПОПАСТЬ");
            }

            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void reads_dates_from_serial_row_not_from_sheet_name() throws Exception {
        byte[] bytes = workbook("1-7 июня", SERIAL_2026_06_01,
                new int[][]{{7, 0}},
                new String[][]{{"Java", null, null, null, null, null, null}}, false);

        var parsed = parser.parse(bytes);

        assertThat(parsed.cells()).hasSize(1);
        // The sheet name «1-7 июня» carries no year at all; the date must come from serial 46174.
        assertThat(parsed.cells().get(0).date()).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    /**
     * The decisive check on the Excel-1900 conversion: serial 46174 is the Monday the file's own
     * row-2 caption calls «Понедельник». An off-by-one in the leap-year compensation shows up here
     * as Sunday 2026-05-31 — which is exactly the error the spike made
     * (`.scratch/xlsx-schedule-import/bugs/01`), and which would have shifted every imported record
     * by one day.
     */
    @Test
    void excel_serial_conversion_matches_the_weekday_written_in_the_file() {
        LocalDate date = XlsxScheduleGridParser.excelSerialToDate(SERIAL_2026_06_01);

        assertThat(date).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(date.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void maps_each_column_to_the_date_written_above_it() throws Exception {
        byte[] bytes = workbook("1-7 июня", SERIAL_2026_06_01,
                new int[][]{{9, 30}},
                new String[][]{{"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"}}, false);

        var parsed = parser.parse(bytes);

        assertThat(parsed.cells()).hasSize(7);
        // Column D holds whatever date row 1 gives it — no fixed "week starts on X" assumption.
        assertThat(parsed.cells()).anySatisfy(cell -> {
            assertThat(cell.activity()).isEqualTo("Пн");
            assertThat(cell.date()).isEqualTo(LocalDate.of(2026, 6, 1));
        });
        assertThat(parsed.cells()).anySatisfy(cell -> {
            assertThat(cell.activity()).isEqualTo("Вс");
            assertThat(cell.date()).isEqualTo(LocalDate.of(2026, 6, 7));
        });
    }

    @Test
    void reads_time_from_column_c_fraction_on_the_15_minute_grid() throws Exception {
        byte[] bytes = workbook("1-7 июня", SERIAL_2026_06_01,
                new int[][]{{7, 0}, {12, 15}, {23, 45}},
                new String[][]{
                        {"Подъём", null, null, null, null, null, null},
                        {"Обед", null, null, null, null, null, null},
                        {"Сон", null, null, null, null, null, null}}, false);

        var parsed = parser.parse(bytes);

        assertThat(parsed.cells()).extracting(XlsxScheduleGridParser.ScheduleCell::time)
                .containsExactlyInAnyOrder(LocalTime.of(7, 0), LocalTime.of(12, 15), LocalTime.of(23, 45));
        // One cell = one 15-minute Запись времени (WOLF's atomic slot).
        var first = parsed.cells().stream().filter(c -> c.activity().equals("Обед")).findFirst().orElseThrow();
        assertThat(first.startAt()).isEqualTo(LocalDate.of(2026, 6, 1).atTime(12, 15));
        assertThat(first.endAt()).isEqualTo(LocalDate.of(2026, 6, 1).atTime(12, 30));
    }

    @Test
    void ignores_the_fact_aggregate_block_at_the_bottom() throws Exception {
        byte[] bytes = workbook("1-7 июня", SERIAL_2026_06_01,
                new int[][]{{7, 0}},
                new String[][]{{"Java", null, null, null, null, null, null}}, true);

        var parsed = parser.parse(bytes);

        assertThat(parsed.cells()).hasSize(1);
        assertThat(parsed.cells()).extracting(XlsxScheduleGridParser.ScheduleCell::activity)
                .containsExactly("Java")
                .doesNotContain("НЕ ДОЛЖНО ПОПАСТЬ");
    }

    @Test
    void skips_empty_cells_and_the_unknown_marker() throws Exception {
        byte[] bytes = workbook("1-7 июня", SERIAL_2026_06_01,
                new int[][]{{7, 0}},
                new String[][]{{"Java", null, "Неизвестно", "  ", "неизвестно", "-/-", "Сон"}}, false);

        var parsed = parser.parse(bytes);

        // «-/-» is the last two sheets' spelling of «Неизвестно» — both mean "nothing recorded".
        assertThat(parsed.cells()).extracting(XlsxScheduleGridParser.ScheduleCell::activity)
                .containsExactlyInAnyOrder("Java", "Сон");
        assertThat(parsed.uniqueActivities()).containsExactlyInAnyOrder("Java", "Сон");
    }

    /**
     * Release 1.4 ticket 01, bug Б-2: in the owner's file the day serials of columns E–J and EVERY
     * time cell are formulas, not literal numbers. Testing {@code getCellType() == NUMERIC} made the
     * parser return zero cells for the whole workbook.
     */
    @Test
    void reads_dates_and_times_written_as_formulas() throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("1-7 июня");

            Row dateRow = sheet.createRow(0);
            dateRow.createCell(3).setCellValue(SERIAL_2026_06_01);
            for (int day = 1; day < 7; day++) {
                // As in the real file: D1 is literal, E1..J1 are "=D1+n".
                dateRow.createCell(3 + day).setCellFormula("D1+" + day);
            }
            Row slot = sheet.createRow(2);
            slot.createCell(2).setCellFormula("TIME(7,0,0)");
            slot.createCell(3).setCellValue("Java");
            slot.createCell(4).setCellValue("Спортзал");

            wb.getCreationHelper().createFormulaEvaluator().evaluateAll();
            wb.write(out);

            var parsed = parser.parse(out.toByteArray());

            assertThat(parsed.cells()).hasSize(2);
            assertThat(parsed.cells()).allSatisfy(cell -> assertThat(cell.time()).isEqualTo(LocalTime.of(7, 0)));
            assertThat(parsed.cells()).extracting(XlsxScheduleGridParser.ScheduleCell::date)
                    .containsExactlyInAnyOrder(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2));
        }
    }

    /**
     * Release 1.4 ticket 01, bug Б-3: the grid does not sit in the same columns on every sheet. The
     * owner's last two sheets shift it two columns left (time in A, days in B–H). With the columns
     * hardcoded to C/D–J those sheets yielded garbage from the neighbouring block — an "activity"
     * literally called «0» — instead of their real content.
     */
    @Test
    void detects_the_grid_columns_per_sheet_instead_of_assuming_them() throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("3-9 августа");

            // Shifted layout: time in column A (0), days in B..H (1..7).
            Row dateRow = sheet.createRow(0);
            for (int day = 0; day < 7; day++) {
                dateRow.createCell(1 + day).setCellValue(46237 + day);
            }
            // A side block further right must not be taken for an eighth day.
            dateRow.createCell(9).setCellValue("Задачи на неделю");

            Row slot = sheet.createRow(2);
            slot.createCell(0).setCellValue(timeFraction(8, 0));
            slot.createCell(1).setCellValue("Сон");
            slot.createCell(2).setCellValue("Сон");
            slot.createCell(9).setCellValue("KNAV");

            wb.write(out);

            var parsed = parser.parse(out.toByteArray());

            assertThat(parsed.cells()).hasSize(2);
            assertThat(parsed.cells()).extracting(XlsxScheduleGridParser.ScheduleCell::activity)
                    .containsOnly("Сон")
                    .doesNotContain("KNAV");
            assertThat(parsed.cells()).allSatisfy(cell -> assertThat(cell.time()).isEqualTo(LocalTime.of(8, 0)));
            assertThat(parsed.cells()).extracting(XlsxScheduleGridParser.ScheduleCell::date)
                    .containsExactlyInAnyOrder(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 4));
        }
    }

    @Test
    void counts_weeks_and_unique_activities_across_sheets() throws Exception {
        // Two sheets, a week apart, sharing one activity.
        byte[] first = workbook("1-7 июня", SERIAL_2026_06_01,
                new int[][]{{7, 0}},
                new String[][]{{"Java", "Спортзал", null, null, null, null, null}}, false);
        byte[] second = workbook("8-14 июня", SERIAL_2026_06_01 + 7,
                new int[][]{{8, 0}},
                new String[][]{{"Java", null, null, null, null, null, null}}, false);

        var firstParsed = parser.parse(first);
        var secondParsed = parser.parse(second);

        assertThat(firstParsed.weeks()).isEqualTo(1);
        assertThat(firstParsed.cellCount()).isEqualTo(2);
        assertThat(firstParsed.uniqueActivities()).containsExactlyInAnyOrder("Java", "Спортзал");
        assertThat(secondParsed.cells().get(0).date()).isEqualTo(LocalDate.of(2026, 6, 8));
    }

    /**
     * The parser must not impose a first weekday: whatever date row 1 puts above a column IS that
     * column's date. (In the owner's file every sheet happens to start on Monday once the shifted
     * layout is read correctly — the "week starts on Sunday/Wednesday" readings were artefacts of
     * the two parsing bugs above, not a property of the data.)
     */
    @Test
    void takes_each_column_date_from_the_file_without_assuming_a_first_weekday() throws Exception {
        // A week deliberately starting on a Wednesday.
        byte[] bytes = workbook("произвольная неделя", 46239,
                new int[][]{{10, 0}},
                new String[][]{{"Отдых", "Java", null, null, null, null, null}}, false);

        var parsed = parser.parse(bytes);

        var first = parsed.cells().stream().filter(c -> c.activity().equals("Отдых")).findFirst().orElseThrow();
        var second = parsed.cells().stream().filter(c -> c.activity().equals("Java")).findFirst().orElseThrow();
        assertThat(first.date()).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(first.date().getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
        assertThat(second.date()).isEqualTo(LocalDate.of(2026, 8, 6));
    }

    @Test
    void sheet_without_date_serials_is_skipped_not_defaulted() throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Справочник");
            Row row = sheet.createRow(2);
            row.createCell(2).setCellValue(timeFraction(7, 0));
            row.createCell(3).setCellValue("Не расписание");
            wb.write(out);

            var parsed = parser.parse(out.toByteArray());

            // The old parser fell back to a hardcoded week here; nothing must be invented.
            assertThat(parsed.cells()).isEmpty();
            assertThat(parsed.weeks()).isZero();
        }
    }

    @Test
    void unreadable_file_reports_a_clear_error() {
        assertThatThrownBy(() -> parser.parse("не xlsx".getBytes()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Не удалось прочитать файл расписания");
    }

    @Test
    void slot_time_snaps_to_the_15_minute_grid() {
        // Excel fractions carry float noise: 0.29166666 is 06:59:59.99 → must land on 07:00.
        assertThat(XlsxScheduleGridParser.excelFractionToTime(0.2916666666)).isEqualTo(LocalTime.of(7, 0));
        assertThat(XlsxScheduleGridParser.excelFractionToTime(0.5)).isEqualTo(LocalTime.of(12, 0));
        assertThat(XlsxScheduleGridParser.excelFractionToTime(0.98958333)).isEqualTo(LocalTime.of(23, 45));
    }

    @Test
    void parsed_cells_keep_the_sheet_name_for_diagnostics() throws Exception {
        byte[] bytes = workbook("15-21 июня", SERIAL_2026_06_01 + 14,
                new int[][]{{7, 0}},
                new String[][]{{"Java", null, null, null, null, null, null}}, false);

        List<XlsxScheduleGridParser.ScheduleCell> cells = parser.parse(bytes).cells();

        assertThat(cells).singleElement()
                .extracting(XlsxScheduleGridParser.ScheduleCell::sheetName)
                .isEqualTo("15-21 июня");
    }
}
