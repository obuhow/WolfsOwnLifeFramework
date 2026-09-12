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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
 * Release 1.4 preview → apply path for the weekly-grid schedule import.
 *
 * <p>The parser still produces one candidate per filled 15-minute cell, but this service
 * materialises contiguous candidates as one continuous {@link TimeEntry} interval. The 15-minute
 * grid remains the source of truth for conflicts and grouping; it is not the storage unit.</p>
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

    /** Candidate enriched with the mapping result used by both preview and apply. */
    private record Candidate(ScheduleCell cell, String activityText, String activityKey,
                             Long deloId, TimeEntry.Status status) {

        ScheduleEntryGrouper.Candidate forGrouping() {
            return new ScheduleEntryGrouper.Candidate(cell, activityKey, deloId, status);
        }
    }

    private record PreservedFragment(TimeEntry source, LocalDateTime startAt, LocalDateTime endAt) {
    }

    /** Parses the file and reports what applying it would do. Writes nothing. */
    @Transactional(readOnly = true)
    public ImportPreviewResponse preview(String username, byte[] fileBytes) {
        User user = currentUser(username);
        ParsedSchedule parsed = parser.parse(fileBytes);
        Map<String, ActivityMapping> mappingByKey = mappingsByKey(user);
        List<Candidate> candidates = resolveCandidates(parsed.cells(), mappingByKey);
        validateUniqueSourceSlots(candidates);
        Map<LocalDateTime, TimeEntry> occupied = occupiedSlots(user, candidates);
        return summarise(parsed, alreadyImported(user, fileBytes), candidates, occupied, mappingByKey);
    }

    /**
     * Applies a previously previewed file. Each contiguous group becomes one time entry with
     * startAt at the first cell and endAt at the end of the last cell.
     */
    @Transactional
    public ImportApplyResponse apply(String username, byte[] fileBytes, String filename,
                                     ImportConflictStrategy conflictStrategy) {
        User user = currentUser(username);
        ParsedSchedule parsed = parser.parse(fileBytes);

        Optional<XlsxImportRun> existing = alreadyImported(user, fileBytes);
        if (existing.isPresent()) {
            XlsxImportRun run = existing.get();
            return new ImportApplyResponse(run.getId(), 0, 0, 0, run.getPendingQuestions(), true, false);
        }
        if (conflictStrategy == ImportConflictStrategy.CANCEL) {
            return new ImportApplyResponse(null, 0, 0, 0, 0, false, true);
        }

        Map<String, ActivityMapping> mappingByKey = mappingsByKey(user);
        List<Candidate> candidates = resolveCandidates(parsed.cells(), mappingByKey);
        validateUniqueSourceSlots(candidates);
        Map<LocalDateTime, TimeEntry> occupied = occupiedSlots(user, candidates);
        List<Candidate> freeCandidates = candidates.stream()
                .filter(candidate -> !occupied.containsKey(candidate.cell().startAt()))
                .toList();
        int skippedOccupied = conflictStrategy == ImportConflictStrategy.SKIP_ALL ? occupied.size() : 0;
        int overwritten = conflictStrategy == ImportConflictStrategy.OVERWRITE_ALL ? occupied.size() : 0;

        XlsxImportRun run = runs.save(XlsxImportRun.builder()
                .user(user)
                .filename(filename == null || filename.isBlank() ? "Расписание.xlsx" : filename)
                .fileHash(sha256(fileBytes))
                .status(XlsxImportRun.Status.DONE)
                .createdAt(Instant.now())
                .build());

        if (conflictStrategy == ImportConflictStrategy.OVERWRITE_ALL) {
            replaceOccupiedEntries(occupied, candidates.stream()
                    .map(candidate -> candidate.cell().startAt())
                    .collect(java.util.stream.Collectors.toSet()));
        }

        List<Candidate> materializedCells = conflictStrategy == ImportConflictStrategy.SKIP_ALL
                ? freeCandidates
                : candidates;
        List<ScheduleEntryGrouper.Group> groups = ScheduleEntryGrouper.group(
                materializedCells.stream().map(Candidate::forGrouping).toList());

        int created = 0;
        int mappedCells = 0;
        int unknownCells = 0;
        for (Candidate candidate : materializedCells) {
            if (mappingByKey.containsKey(candidate.activityKey())) {
                mappedCells++;
            } else {
                unknownCells++;
            }
        }
        for (ScheduleEntryGrouper.Group group : groups) {
            Candidate first = candidateFor(group, materializedCells);
            ActivityMapping mapping = mappingByKey.get(first.activityKey());
            entries.save(TimeEntry.builder()
                    .user(user)
                    .delo(mapping == null ? null : mapping.getDelo())
                    .startAt(group.startAt())
                    .endAt(group.endAt())
                    .status(first.status())
                    .build());
            created++;
            if (mapping == null) {
                saveQuestion(run, first.activityText(), first.cell());
            }
        }

        run.setTotalCells(parsed.cellCount());
        run.setMapped(mappedCells);
        run.setUnknown(unknownCells);
        int pending = unresolvedActivityCount(run.getId());
        run.setPendingQuestions(pending);
        run.setStatus(pending > 0 ? XlsxImportRun.Status.PAUSED : XlsxImportRun.Status.DONE);
        runs.save(run);

        return new ImportApplyResponse(run.getId(), created, skippedOccupied, overwritten, pending, false, false);
    }

    /** Builds a preview using SKIP_ALL semantics, the safe default used by apply. */
    private ImportPreviewResponse summarise(ParsedSchedule parsed, Optional<XlsxImportRun> existingRun,
                                            List<Candidate> candidates,
                                            Map<LocalDateTime, TimeEntry> occupied,
                                            Map<String, ActivityMapping> mappingByKey) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            counts.merge(candidate.activityKey(), 1, Integer::sum);
            labels.putIfAbsent(candidate.activityKey(), candidate.activityText());
        }

        List<ActivityPreview> activities = new ArrayList<>();
        int known = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            ActivityMapping mapping = mappingByKey.get(entry.getKey());
            if (mapping != null) {
                known++;
            }
            Delo delo = mapping == null ? null : mapping.getDelo();
            activities.add(new ActivityPreview(labels.get(entry.getKey()), entry.getValue(),
                    mapping != null, delo == null ? null : delo.getTitle()));
        }
        activities.sort(Comparator.comparingInt(ActivityPreview::cells).reversed()
                .thenComparing(ActivityPreview::activityText));

        List<Candidate> freeCandidates = candidates.stream()
                .filter(candidate -> !occupied.containsKey(candidate.cell().startAt()))
                .toList();
        LocalDate from = candidates.stream().map(candidate -> candidate.cell().date())
                .min(Comparator.naturalOrder()).orElse(null);
        LocalDate to = candidates.stream().map(candidate -> candidate.cell().date())
                .max(Comparator.naturalOrder()).orElse(null);

        return new ImportPreviewResponse(
                parsed.weeks(),
                parsed.cellCount(),
                from,
                to,
                known,
                activities.size() - known,
                existingRun.isPresent() ? 0 : groupedCount(freeCandidates),
                occupied.size(),
                existingRun.isPresent(),
                List.copyOf(activities));
    }

    private static int groupedCount(List<Candidate> candidates) {
        return ScheduleEntryGrouper.group(candidates.stream()
                .map(Candidate::forGrouping).toList()).size();
    }

    private static void validateUniqueSourceSlots(List<Candidate> candidates) {
        ScheduleEntryGrouper.ensureUniqueStarts(candidates.stream()
                .map(Candidate::forGrouping).toList());
    }

    private List<Candidate> resolveCandidates(List<ScheduleCell> cells,
                                              Map<String, ActivityMapping> mappingByKey) {
        return cells.stream().map(cell -> {
            String activityText = ActivityTextNormalizer.normalize(cell.activity());
            String activityKey = ActivityTextNormalizer.key(activityText);
            ActivityMapping mapping = mappingByKey.get(activityKey);
            return new Candidate(cell, activityText, activityKey,
                    mapping == null ? null : mapping.getDelo().getId(),
                    mapping == null ? TimeEntry.Status.UNKNOWN : TimeEntry.Status.DONE);
        }).toList();
    }

    private Map<String, ActivityMapping> mappingsByKey(User user) {
        Map<String, ActivityMapping> result = new LinkedHashMap<>();
        for (ActivityMapping mapping : mappings.findByUserOrderByIdAsc(user)) {
            result.putIfAbsent(ActivityTextNormalizer.key(mapping.getActivityText()), mapping);
        }
        return result;
    }

    private Map<LocalDateTime, TimeEntry> occupiedSlots(User user, List<Candidate> candidates) {
        Map<LocalDateTime, TimeEntry> occupied = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            LocalDateTime startAt = candidate.cell().startAt();
            entries.findCoveringSlot(user.getId(), startAt).ifPresent(entry -> occupied.put(startAt, entry));
        }
        return occupied;
    }

    private Candidate candidateFor(ScheduleEntryGrouper.Group group, List<Candidate> candidates) {
        LocalDateTime startAt = group.startAt();
        return candidates.stream()
                .filter(candidate -> candidate.cell().startAt().equals(startAt)
                        && candidate.activityKey().equals(group.first().activityKey())
                        && Objects.equals(candidate.deloId(), group.first().deloId())
                        && candidate.status() == group.first().status())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Группа импорта не содержит исходную ячейку"));
    }

    /**
     * Replaces only the occupied 15-minute portions. Uncovered fragments of an existing long entry
     * survive as separate intervals, so overwrite never erases facts outside the imported cells.
     */
    private void replaceOccupiedEntries(Map<LocalDateTime, TimeEntry> occupied,
                                        Set<LocalDateTime> importedSlots) {
        Set<TimeEntry> entriesToReplace = new LinkedHashSet<>(occupied.values());
        List<PreservedFragment> fragments = new ArrayList<>();
        for (TimeEntry existing : entriesToReplace) {
            entries.delete(existing);
            LocalDateTime cursor = existing.getStartAt();
            LocalDateTime fragmentStart = null;
            while (cursor.isBefore(existing.getEndAt())) {
                LocalDateTime next = cursor.plusMinutes(XlsxScheduleGridParser.SLOT_MINUTES);
                boolean overwritten = importedSlots.contains(cursor);
                if (!overwritten && fragmentStart == null) {
                    fragmentStart = cursor;
                }
                if (overwritten && fragmentStart != null) {
                    fragments.add(new PreservedFragment(existing, fragmentStart, cursor));
                    fragmentStart = null;
                }
                cursor = next;
            }
            if (fragmentStart != null) {
                fragments.add(new PreservedFragment(existing, fragmentStart, existing.getEndAt()));
            }
        }
        // The schema has a unique (user_id, start_at) index. Flush all removals before restoring
        // fragments or inserting the imported interval at a replaced start.
        entries.flush();
        for (PreservedFragment fragment : fragments) {
            savePreservedFragment(fragment.source(), fragment.startAt(), fragment.endAt());
        }
    }

    private void savePreservedFragment(TimeEntry source, LocalDateTime startAt, LocalDateTime endAt) {
        entries.save(TimeEntry.builder()
                .user(source.getUser())
                .delo(source.getDelo())
                .adHocText(source.getAdHocText())
                .startAt(startAt)
                .endAt(endAt)
                .status(source.getStatus())
                .build());
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

    private int unresolvedActivityCount(Long importRunId) {
        return (int) questions.findByImportRunIdAndResolvedFalseOrderByStartAtAsc(importRunId).stream()
                .map(question -> ActivityTextNormalizer.key(question.getActivityText()))
                .distinct()
                .count();
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
