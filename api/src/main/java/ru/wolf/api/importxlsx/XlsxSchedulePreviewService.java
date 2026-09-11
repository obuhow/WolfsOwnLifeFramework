/*
 * WOLF — Wolf's Own Life Framework
 * Copyright (C) 2025 Pavel Obukhov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
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
 * Release 1.4 ticket 02 + 03: the preview → apply path for the weekly-grid schedule import
 * (decision И-I), with conflict resolution on overlapping imports (decision И-G/H).
 *
 * <p>Splits the single "upload writes immediately" step of {@link XlsxImportService} into two:
 *
 * <ol>
 *   <li>{@link #preview(String, byte[])} parses the file with {@link XlsxScheduleGridParser} and
 *       counts what WOULD be created — no database writes at all — including how many cells would
 *       conflict with an existing Запись времени;</li>
 *   <li>{@link #apply(String, byte[], String, ImportConflictStrategy)} materialises the cells as
 *       individual 15-minute {@code DONE} Записи времени (И-D: one cell = one Запись, the model
 *       does not change), honouring the chosen conflict strategy for the slots that already hold
 *       a Запись.</li>
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
     * import did, so the existing "resolve" flow keeps working.</p>
     *
     * <p>When a cell's slot is already covered by an existing Запись времени, the behaviour depends
     * on {@code conflictStrategy} (decision И-G/H: one conscious choice for the whole import):
     *
     * <ul>
     *   <li>{@link ImportConflictStrategy#SKIP_ALL} (default until this ticket landed) — the occupied
     *       slot is left untouched and counted as {@code skippedOccupied};</li>
     *   <li>{@link ImportConflictStrategy#OVERWRITE_ALL} — the existing Запись's time and Дело are
     *       replaced by the file's data, counted as {@code overwritten};</li>
     *   <li>{@link ImportConflictStrategy#CANCEL} — nothing is written, the import is aborted.</li>
     * </ul>
     */
    @Transactional
    public ImportApplyResponse apply(String username, byte[] fileBytes, String filename,
                                     ImportConflictStrategy conflictStrategy) {
        User user = currentUser(username);
        ParsedSchedule parsed = parser.parse(fileBytes);

        Optional<XlsxImportRun> existing = alreadyImported(user, fileBytes);
        if (existing.isPresent()) {
            // Dedup by hash, kept from the original import: re-applying the same file is a no-op
            // rather than a second copy of the same half-year.
            XlsxImportRun run = existing.get();
            return new ImportApplyResponse(run.getId(), 0, 0, 0, run.getPendingQuestions(), true, false);
        }

        // CANCEL is a deliberate "не сейчас": the file is not corrupt, there is just nothing to do.
        if (conflictStrategy == ImportConflictStrategy.CANCEL) {
            return new ImportApplyResponse(null, 0, 0, 0, 0, false, true);
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
        int overwritten = 0;
        int unknown = 0;
        boolean overwrite = conflictStrategy == ImportConflictStrategy.OVERWRITE_ALL;
        Map<String, String> activityLabels = activityLabels(parsed.cells());
        for (ScheduleCell cell : parsed.cells()) {
            String activityText = activityLabels.get(ActivityTextNormalizer.key(cell.activity()));
            Optional<TimeEntry> existingEntry = entries.findByUserIdAndStartAt(user.getId(), cell.startAt());
            if (existingEntry.isPresent()) {
                if (overwrite) {
                    TimeEntry e = existingEntry.get();
                    ActivityMapping mapping = findMapping(user, activityText);
                    e.setDelo(mapping == null ? null : mapping.getDelo());
                    e.setStartAt(cell.startAt());
                    e.setEndAt(cell.endAt());
                    e.setStatus(mapping == null ? TimeEntry.Status.UNKNOWN : TimeEntry.Status.DONE);
                    entries.save(e);
                    overwritten++;
                    if (mapping == null) {
                        unknown++;
                        saveQuestion(run, activityText, cell);
                    }
                    continue;
                }
                // Default / SKIP_ALL: leave the existing fact untouched.
                skippedOccupied++;
                continue;
            }
            ActivityMapping mapping = findMapping(user, activityText);
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
                saveQuestion(run, activityText, cell);
            }
        }

        run.setTotalCells(parsed.cellCount());
        run.setMapped(created + overwritten - unknown);
        run.setUnknown(unknown);
        int pending = unresolvedActivityCount(run.getId());
        run.setPendingQuestions(pending);
        run.setStatus(pending > 0 ? XlsxImportRun.Status.PAUSED : XlsxImportRun.Status.DONE);
        runs.save(run);

        return new ImportApplyResponse(run.getId(), created, skippedOccupied, overwritten, pending, false, false);
    }

    /** Builds the preview summary from parsed cells; every number is counted, never estimated. */
    private ImportPreviewResponse summarise(User user, ParsedSchedule parsed,
                                            Optional<XlsxImportRun> existingRun) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> activityLabels = activityLabels(parsed.cells());
        int conflictingCells = 0;
        for (ScheduleCell cell : parsed.cells()) {
            String key = ActivityTextNormalizer.key(cell.activity());
            counts.merge(key, 1, Integer::sum);
            if (entries.findByUserIdAndStartAt(user.getId(), cell.startAt()).isPresent()) {
                conflictingCells++;
            }
        }

        List<ActivityPreview> activities = new ArrayList<>();
        int known = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            String activityText = activityLabels.get(entry.getKey());
            ActivityMapping mapping = findMapping(user, activityText);
            Delo delo = mapping == null ? null : mapping.getDelo();
            if (mapping != null) {
                known++;
            }
            activities.add(new ActivityPreview(activityText, entry.getValue(),
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
                conflictingCells,
                existingRun.isPresent(),
                List.copyOf(activities));
    }

    private void saveQuestion(XlsxImportRun run, String activityText, ScheduleCell cell) {
        questions.save(XlsxImportQuestion.builder()
                .importRun(run)
                .activityText(activityText)
                .sheetName(cell.sheetName())
                .startAt(cell.startAt())
                .resolved(false)
                .build());
    }

    private ActivityMapping findMapping(User user, String activityText) {
        return mappings.findByUserAndNormalizedActivityText(user, activityText).orElse(null);
    }

    private int unresolvedActivityCount(Long importRunId) {
        return (int) questions.findByImportRunIdAndResolvedFalseOrderByStartAtAsc(importRunId).stream()
                .map(question -> ActivityTextNormalizer.key(question.getActivityText()))
                .distinct()
                .count();
    }

    private static Map<String, String> activityLabels(List<ScheduleCell> cells) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (ScheduleCell cell : cells) {
            String normalized = ActivityTextNormalizer.normalize(cell.activity());
            labels.putIfAbsent(ActivityTextNormalizer.key(normalized), normalized);
        }
        return labels;
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
