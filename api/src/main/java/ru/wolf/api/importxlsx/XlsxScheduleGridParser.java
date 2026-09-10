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

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

/**
 * Release 1.4 ticket 01: deterministic parser for the owner's real {@code Расписание.xlsx} weekly
 * grid. Reads the workbook into an intermediate model — a flat list of
 * {@link ScheduleCell}{@code (date, time, activity)} — ready to be materialised into Записи времени
 * by a later ticket. Nothing here touches the database.
 *
 * <p>The format was established by the spike (`.scratch/xlsx-schedule-import/spike/RESULT.md`,
 * `sample/ANALYSIS.md`) and differs from every assumption the old inline parser in
 * {@link XlsxImportService} made:
 *
 * <ul>
 *   <li><b>Dates come from row 1</b> as Excel serial numbers in columns D–J. The sheet NAME
 *       («1-7 июня») carries no year and must not be parsed — the previous
 *       {@code parseWeek(sheetName)} silently fell back to a hardcoded date, collapsing every week
 *       onto one day.</li>
 *   <li><b>Row 2</b> holds the day captions («Понедельник…») and is not data.</li>
 *   <li><b>Days live in columns D–J</b>, not A–G. Which weekday each column is comes from the date
 *       written above it, not from a rule: most sheets start on Monday, but two of the owner's
 *       eleven start on a Wednesday. The parser therefore never assumes a first weekday.</li>
 *   <li><b>Time lives in column C</b> as an Excel day fraction (0.2916… = 07:00). The row number
 *       says nothing about the time: sheets start at different hours and slot rows are not
 *       contiguous, so the old {@code MIDNIGHT.plusMinutes(rowNum * 15)} arithmetic was wrong.
 *       Times across the owner's file span the full 00:00–23:45 range, all on the 15-minute grid.</li>
 *   <li><b>Only slot rows count</b>: a row is data only when column C holds a time fraction. This is
 *       what keeps the «Факт» aggregate block at the bottom of each sheet out of the import.</li>
 * </ul>
 *
 * <p>Empty cells and the «Неизвестно» marker produce nothing (decision И-C: "пусто = неизвестно" is
 * an interpretation for the UI/agent, not a materialised record).
 */
@Component
public class XlsxScheduleGridParser {

    /**
     * Activity markers that carry no fact and must never become a Запись времени (И-C).
     *
     * <p>«Неизвестно» is the marker the spike found (46 cells). «-/-» is the same idea written
     * differently on the workbook's last two sheets (202 cells) — the spike never saw it because it
     * mis-parsed those sheets entirely. Both mean "nothing recorded here", so both are skipped:
     * "пусто = неизвестно" stays an interpretation for the UI/agent, not a materialised record.
     */
    private static final Set<String> SKIPPED_ACTIVITIES = Set.of("неизвестно", "-/-");

    /** Number of day columns in a week grid. */
    private static final int DAYS_IN_WEEK = 7;
    /** Row 1 (0-based 0) — the date serials. Row 2 (0-based 1) — day captions. */
    private static final int DATE_ROW = 0;
    /** A serial this large is a real date rather than a stray small number (~2009 and later). */
    private static final int MIN_DATE_SERIAL = 40000;
    /** WOLF's atomic slot: one cell = one 15-minute Запись времени. */
    public static final int SLOT_MINUTES = 15;

    /**
     * Where one sheet's grid actually sits.
     *
     * <p>The layout is NOT fixed across the workbook: the owner's first nine sheets put the time in
     * column C with days in D–J, while the last two shift everything two columns left (time in A,
     * days in B–H). Hardcoding D–J silently harvested garbage from those two sheets — numbers out of
     * the neighbouring block showed up as an activity called «0». So the layout is detected per
     * sheet from the date row instead of assumed.
     *
     * @param timeColumn   column holding the slot time as an Excel day fraction
     * @param datesByColumn column → date, taken from the serials written in row 1
     */
    private record SheetLayout(int timeColumn, Map<Integer, LocalDate> datesByColumn) {

        boolean isEmpty() {
            return datesByColumn.isEmpty();
        }
    }

    /**
     * One filled grid cell: a concrete moment plus the activity text written there.
     *
     * @param date     calendar date the cell's column maps to
     * @param time     slot start, taken from column C and rounded to the 15-minute grid
     * @param activity trimmed activity text exactly as written in the sheet (normalisation of
     *                 synonyms is ticket 05's job, not the parser's)
     * @param sheetName sheet the cell came from — kept for import questions and diagnostics
     */
    public record ScheduleCell(LocalDate date, LocalTime time, String activity, String sheetName) {

        /** Start of the Запись времени this cell becomes. */
        public LocalDateTime startAt() {
            return date.atTime(time);
        }

        /** End of the Запись времени this cell becomes (one 15-minute slot). */
        public LocalDateTime endAt() {
            return startAt().plusMinutes(SLOT_MINUTES);
        }
    }

    /**
     * Result of reading a workbook: the flat cell list plus the counts the preview ticket needs.
     *
     * @param cells              every filled activity cell, in sheet then time order
     * @param weeks              how many sheets yielded at least one dated week
     * @param uniqueActivities   distinct activity texts, in first-seen order
     */
    public record ParsedSchedule(List<ScheduleCell> cells, int weeks, Set<String> uniqueActivities) {

        public int cellCount() {
            return cells.size();
        }
    }

    /** Reads the workbook bytes into the intermediate model. */
    public ParsedSchedule parse(byte[] fileBytes) {
        List<ScheduleCell> cells = new ArrayList<>();
        Set<String> activities = new LinkedHashSet<>();
        int weeks = 0;

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(fileBytes))) {
            DataFormatter formatter = new DataFormatter();
            for (Sheet sheet : workbook) {
                SheetLayout layout = detectLayout(sheet);
                if (layout.isEmpty()) {
                    // Not a schedule sheet (no date serials) — skip rather than invent a week.
                    continue;
                }
                weeks++;
                collectSheetCells(sheet, layout, formatter, cells, activities);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Не удалось прочитать файл расписания: " + e.getMessage(), e);
        }
        return new ParsedSchedule(List.copyOf(cells), weeks, Set.copyOf(activities));
    }

    /**
     * Detects one sheet's layout from its date row: every column of row 1 holding a date serial is a
     * day column, and the time column is the one immediately left of the first of them.
     *
     * <p>Reading the columns as written also means the weekday order needs no rule at all — column D
     * is simply whatever date row 1 puts above it.
     */
    private SheetLayout detectLayout(Sheet sheet) {
        Map<Integer, LocalDate> dates = new HashMap<>();
        Row dateRow = sheet.getRow(DATE_ROW);
        if (dateRow == null) {
            return new SheetLayout(-1, dates);
        }
        for (int column = dateRow.getFirstCellNum(); column < dateRow.getLastCellNum(); column++) {
            Cell cell = dateRow.getCell(column);
            if (!isNumeric(cell)) {
                continue;
            }
            double serial = cell.getNumericCellValue();
            if (serial >= MIN_DATE_SERIAL) {
                dates.put(column, excelSerialToDate((int) serial));
            }
        }
        if (dates.isEmpty()) {
            return new SheetLayout(-1, dates);
        }
        // Keep only the seven leftmost day columns: anything further right belongs to side blocks
        // («Задачи на неделю» and friends), which must not be mistaken for an eighth day.
        List<Integer> dayColumns = dates.keySet().stream().sorted().limit(DAYS_IN_WEEK).toList();
        Map<Integer, LocalDate> week = new HashMap<>();
        for (Integer column : dayColumns) {
            week.put(column, dates.get(column));
        }
        return new SheetLayout(dayColumns.get(0) - 1, week);
    }

    /**
     * True when the cell holds a number — either literally or as a formula's cached result.
     *
     * <p>This distinction is not academic in the owner's file: only column D of the date row is a
     * literal number, while E–J are formulas ({@code =D1+1}) and EVERY time cell in column C is a
     * formula too. A plain {@code getCellType() == NUMERIC} test therefore sees almost nothing and
     * the import silently yields zero cells. The spike never hit this because it read the raw XML,
     * where a formula's cached {@code <v>} is indistinguishable from a literal value.
     */
    private boolean isNumeric(Cell cell) {
        if (cell == null) {
            return false;
        }
        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType()
                : cell.getCellType();
        return type == CellType.NUMERIC;
    }

    /** Walks the slot rows of one sheet, emitting a cell per filled activity. */
    private void collectSheetCells(Sheet sheet, SheetLayout layout,
                                   DataFormatter formatter, List<ScheduleCell> cells,
                                   Set<String> activities) {
        for (Row row : sheet) {
            if (row.getRowNum() <= DATE_ROW) {
                continue;
            }
            LocalTime time = slotTime(row, layout.timeColumn());
            if (time == null) {
                // The time column is not a fraction → header, spacer, or the «Факт» aggregate block.
                continue;
            }
            for (Map.Entry<Integer, LocalDate> day : layout.datesByColumn().entrySet()) {
                Cell cell = row.getCell(day.getKey());
                if (cell == null) {
                    continue;
                }
                String activity = formatter.formatCellValue(cell).trim();
                if (activity.isEmpty() || SKIPPED_ACTIVITIES.contains(activity.toLowerCase())) {
                    continue;
                }
                cells.add(new ScheduleCell(day.getValue(), time, activity, sheet.getSheetName()));
                activities.add(activity);
            }
        }
    }

    /**
     * Reads the slot time from column C. Returns {@code null} when the row is not a slot row, which
     * is the test that keeps the «Факт» block and any stray rows out of the import.
     */
    private LocalTime slotTime(Row row, int timeColumn) {
        if (timeColumn < 0) {
            return null;
        }
        Cell timeCell = row.getCell(timeColumn);
        if (!isNumeric(timeCell)) {
            return null;
        }
        double fraction = timeCell.getNumericCellValue();
        if (fraction < 0 || fraction >= 1) {
            // A whole number here is an aggregate (hours in the «Факт» block), not a time of day.
            return null;
        }
        return excelFractionToTime(fraction);
    }

    /**
     * Excel 1900 serial → date.
     *
     * <p>Excel wrongly believes 1900 was a leap year, so serials from 60 onwards are one day ahead
     * of a naive count; the standard conversion absorbs that by anchoring at 1899-12-30. Every date
     * in the owner's file is far past that point, and this conversion is what makes serial 46174
     * come out as 2026-06-01 — the Monday the sheet's own row-2 caption declares.
     *
     * <p>NB: the spike script subtracted an extra day on top of this anchor and therefore reported
     * 2026-05-31 (Sunday), which is where its "column D = воскресенье" conclusion came from. Both
     * were wrong by exactly one day; see {@code bugs/01} of this release.
     */
    static LocalDate excelSerialToDate(int serial) {
        return LocalDate.of(1899, 12, 30).plusDays(serial);
    }

    /** Excel day fraction → time, snapped to the 15-minute grid WOLF stores. */
    static LocalTime excelFractionToTime(double fraction) {
        long totalMinutes = Math.round(fraction * 24 * 60);
        long snapped = Math.round(totalMinutes / (double) SLOT_MINUTES) * SLOT_MINUTES;
        return LocalTime.MIN.plusMinutes(snapped % (24 * 60));
    }
}
