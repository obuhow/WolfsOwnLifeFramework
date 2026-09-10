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

import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.importxlsx.XlsxScheduleGridParser.ParsedSchedule;
import ru.wolf.api.importxlsx.XlsxScheduleGridParser.ScheduleCell;
import ru.wolf.api.importxlsx.dto.ImportApplyResponse;
import ru.wolf.api.importxlsx.dto.ImportPreviewResponse;
import ru.wolf.api.importxlsx.dto.ImportPreviewResponse.ActivityPreview;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

/**
 * Release 1.4 ticket 02: the preview → apply path for the weekly-grid schedule import
 * (decision И-I).
 *
 * <p>Splits the single "upload writes immediately" step of {@link XlsxImportService} into two:
 *
 * <ol>
 *   <li>{@link #preview(String, byte[])} parses the file with {@link XlsxScheduleGridParser} and
 *       counts what WOULD be created — no database writes at all;</li>
 *   <li>{@link #apply(String, byte[], String)} materialises the cells as individual 15-minute
 *       {@code DONE} Записи времени (И-D: one cell = one Запись, the model does not change).</li>
 * </ol>
 *
 * <p>The existing machinery is reused rather than rewritten: {@code activity_mapping} decides which
 * activities are known, {@code xlsx_import_question} carries the unknown ones, and the run is still
 * deduped by file hash.
 */
@Service
@RequiredArgsConstructor
public class XlsxSchedulePreviewService {

    private final UserRepository users;
    private final TimeEntryRepository entries;
    private final ActivityMappingRepository mappings;
    private final XlsxImportRunRepository runs;
    private final XlsxImportQuestionRepository questions;
    private final XlsxScheduleGridParser parser;

    /** Parses the file and reports what applying it would do. Writes nothing. */
    @Transactional(readOnly = true)
    public ImportPreviewResponse preview(String username, byte[] fileBytes) {
        User user = currentUser(username);
        ParsedSchedule parsed = parser.parse(fileBytes);
        return summarise(user, parsed, alreadyImported(user, fileBytes));
    }

    /**
     * Applies a previously previewed file: creates one 15-minute Запись времени per parsed cell.
     *
     * <p>Cells whose activity is already mapped become {@code DONE} entries linked to that Дело;
     * unknown activities still produce an entry plus an import question, exactly as the original
     * import did, so the existing "resolve" flow keeps working. A slot already occupied by any entry
     * is skipped and counted — conflict strategies are ticket 03's job, and until it lands the safe
     * default is to leave existing facts untouched.
     */
    @Transactional
    public ImportApplyResponse apply(String username, byte[] fileBytes, String filename) {
        User user = currentUser(username);
        ParsedSchedule parsed = parser.parse(fileBytes);

        Optional<XlsxImportRun> existing = alreadyImported(user, fileBytes);
        if (existing.isPresent()) {
            // Dedup by hash, kept from the original import: re-applying the same file is a no-op
            // rather than a second copy of the same half-year.
            XlsxImportRun run = existing.get();
            return new ImportApplyResponse(run.getId(), 0, 0, run.getPendingQuestions(), true);
        }

        XlsxImportRun run = runs.save(XlsxImportRun.builder()
                .user(user)
                .filename(filename == null || filename.isBlank() ? "Расписание.xlsx" : filename)
                .fileHash(sha256(fileBytes))
                .status(XlsxImportRun.Status.DONE)
                .createdAt(Instant.now())
                .build());

        int created = 0;
        int skippedOccupied = 0;
        int unknown = 0;
        for (ScheduleCell cell : parsed.cells()) {
            if (entries.findByUserIdAndStartAt(user.getId(), cell.startAt()).isPresent()) {
                skippedOccupied++;
                continue;
            }
            ActivityMapping mapping = mappings.findByUserAndActivityText(user, cell.activity()).orElse(null);
            entries.save(TimeEntry.builder()
                    .user(user)
                    .delo(mapping == null ? null : mapping.getDelo())
                    .startAt(cell.startAt())
                    .endAt(cell.endAt())
                    .status(mapping == null ? TimeEntry.Status.UNKNOWN : TimeEntry.Status.DONE)
                    .build());
            created++;
            if (mapping == null) {
                unknown++;
                questions.save(XlsxImportQuestion.builder()
                        .importRun(run)
                        .activityText(cell.activity())
                        .sheetName(cell.sheetName())
                        .startAt(cell.startAt())
                        .resolved(false)
                        .build());
            }
        }

        run.setTotalCells(parsed.cellCount());
        run.setMapped(created - unknown);
        run.setUnknown(unknown);
        int pending = questions.findByImportRunIdAndResolvedFalseOrderByStartAtAsc(run.getId()).size();
        run.setPendingQuestions(pending);
        run.setStatus(pending > 0 ? XlsxImportRun.Status.PAUSED : XlsxImportRun.Status.DONE);
        runs.save(run);

        return new ImportApplyResponse(run.getId(), created, skippedOccupied, pending, false);
    }

    /** Builds the preview summary from parsed cells; every number is counted, never estimated. */
    private ImportPreviewResponse summarise(User user, ParsedSchedule parsed,
                                            Optional<XlsxImportRun> existingRun) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ScheduleCell cell : parsed.cells()) {
            counts.merge(cell.activity(), 1, Integer::sum);
        }

        List<ActivityPreview> activities = new ArrayList<>();
        int known = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            ActivityMapping mapping = mappings.findByUserAndActivityText(user, entry.getKey()).orElse(null);
            Delo delo = mapping == null ? null : mapping.getDelo();
            if (mapping != null) {
                known++;
            }
            activities.add(new ActivityPreview(entry.getKey(), entry.getValue(),
                    mapping != null, delo == null ? null : delo.getTitle()));
        }
        activities.sort(Comparator.comparingInt(ActivityPreview::cells).reversed()
                .thenComparing(ActivityPreview::activityText));

        LocalDate from = parsed.cells().stream().map(ScheduleCell::date).min(Comparator.naturalOrder()).orElse(null);
        LocalDate to = parsed.cells().stream().map(ScheduleCell::date).max(Comparator.naturalOrder()).orElse(null);

        return new ImportPreviewResponse(
                parsed.weeks(),
                parsed.cellCount(),
                from,
                to,
                known,
                activities.size() - known,
                parsed.cellCount(),
                existingRun.isPresent(),
                List.copyOf(activities));
    }

    private Optional<XlsxImportRun> alreadyImported(User user, byte[] fileBytes) {
        return runs.findByUserAndFileHash(user, sha256(fileBytes));
    }

    private User currentUser(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }
}
