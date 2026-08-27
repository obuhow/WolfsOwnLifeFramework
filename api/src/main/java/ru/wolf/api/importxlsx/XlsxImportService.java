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

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
public class XlsxImportService {
    private final UserRepository users;
    private final DeloRepository delos;
    private final TimeEntryRepository entries;
    private final ActivityMappingRepository mappings;
    private final XlsxImportRunRepository runs;
    private final XlsxImportQuestionRepository questions;

    public XlsxImportService(UserRepository users, DeloRepository delos, TimeEntryRepository entries,
                                ActivityMappingRepository mappings, XlsxImportRunRepository runs,
                                XlsxImportQuestionRepository questions) {
        this.users = users; this.delos = delos; this.entries = entries; this.mappings = mappings; this.runs = runs; this.questions = questions;
    }

    @Transactional
    public ImportResponse upload(String username, byte[] fileBytes, String filename) throws Exception {
        User user = current(username);
        String hash = sha256(fileBytes);
        Optional<XlsxImportRun> existing = runs.findByUserAndFileHash(user, hash);
        if (existing.isPresent()) return toResponse(existing.get());
        XlsxImportRun run = runs.save(XlsxImportRun.builder().user(user).filename(filename == null ? "import.xlsx" : filename)
                .fileHash(hash).status(XlsxImportRun.Status.DONE).build());
        int cells = 0, mapped = 0, unknown = 0;
        try (Workbook workbook = WorkbookFactory.create(new java.io.ByteArrayInputStream(fileBytes))) {
            DataFormatter formatter = new DataFormatter();
            for (Sheet sheet : workbook) {
                LocalDate week = parseWeek(sheet.getSheetName());
                for (Row row : sheet) {
                    if (row.getRowNum() == 0) continue;
                    LocalTime time = LocalTime.MIDNIGHT.plusMinutes((long) Math.max(0, row.getRowNum() - 1) * 15);
                    for (int day = 0; day < 7; day++) {
                        String text = formatter.formatCellValue(row.getCell(day)).trim();
                        cells++;
                        LocalDateTime start = week.plusDays(day).atTime(time);
                        if (entries.findByUserIdAndStartAt(user.getId(), start).isPresent()) continue;
                        ActivityMapping mapping = text.isBlank() ? null : mappings.findByUserAndActivityText(user, text).orElse(null);
                        if (mapping == null) {
                            entries.save(TimeEntry.builder().user(user).startAt(start).endAt(start.plusMinutes(15)).status(TimeEntry.Status.UNKNOWN).build());
                            unknown++;
                            if (!text.isBlank()) questions.save(XlsxImportQuestion.builder().importRun(run).activityText(text).sheetName(sheet.getSheetName()).startAt(start).resolved(false).build());
                        } else {
                            entries.save(TimeEntry.builder().user(user).delo(mapping.getDelo()).startAt(start).endAt(start.plusMinutes(15)).status(TimeEntry.Status.DONE).build());
                            mapped++;
                        }
                    }
                }
            }
        }
        run.setTotalCells(cells); run.setMapped(mapped); run.setUnknown(unknown);
        run.setPendingQuestions(questions.findByImportRunIdAndResolvedFalseOrderByStartAtAsc(run.getId()).size());
        run.setStatus(run.getPendingQuestions() > 0 ? XlsxImportRun.Status.PAUSED : XlsxImportRun.Status.DONE);
        runs.save(run);
        return toResponse(run);
    }

    @Transactional(readOnly = true)
    public ImportResponse get(String username, Long id) {
        User user = current(username); 
        XlsxImportRun run = runs.findByUserAndId(user, id).orElseThrow();
        return toResponse(run);
    }

    @Transactional
    public ImportResponse resolve(String username, Long id, ResolveRequest request) {
        User user = current(username); 
        XlsxImportRun run = runs.findByUserAndId(user, id).orElseThrow();
        Delo delo;
        if (request.deloId() != null) delo = delos.findByUserAndId(user, request.deloId()).orElseThrow();
        else { delo = delos.save(Delo.builder().user(user).title(request.createDelo().title()).build()); }
        ActivityMapping mapping = mappings.findByUserAndActivityText(user, request.activityText()).orElseGet(() -> mappings.save(ActivityMapping.builder().user(user).activityText(request.activityText()).delo(delo).build()));
        for (XlsxImportQuestion q : questions.findByImportRunIdAndResolvedFalseOrderByStartAtAsc(id)) if (q.getActivityText().equals(request.activityText())) { entries.findByUserIdAndStartAt(user.getId(), q.getStartAt()).ifPresent(e -> { e.setDelo(mapping.getDelo()); e.setStatus(TimeEntry.Status.DONE); entries.save(e); }); q.setResolved(true); questions.save(q); }
        run.setPendingQuestions(questions.findByImportRunIdAndResolvedFalseOrderByStartAtAsc(id).size()); run.setStatus(run.getPendingQuestions() == 0 ? XlsxImportRun.Status.DONE : XlsxImportRun.Status.PAUSED); runs.save(run);
        return toResponse(run);
    }

    private User current(String username) { return users.findByUsername(username).orElseThrow(); }
    private static String sha256(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static LocalDate parseWeek(String name) { try { return LocalDate.parse(name.replaceAll(".*?(\\d{4}-\\d{2}-\\d{2}).*", "$1")); } catch (Exception e) { return LocalDate.of(2026, 4, 6); } }
    private ImportResponse toResponse(XlsxImportRun r) { return new ImportResponse(r.getId(), r.getStatus().name(), r.getTotalCells(), r.getMapped(), r.getUnknown(), r.getPendingQuestions()); }
    public record ImportResponse(Long id, String status, int totalCells, int mapped, int unknown, int pendingQuestions) {}
    public record ResolveRequest(String activityText, Long deloId, CreateDelo createDelo) {}
    public record CreateDelo(String title, Long projectId) {}
}
