package ru.wolf.api.importxlsx;

import org.apache.poi.ss.usermodel.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
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

@RestController
@RequestMapping("/api/v1/import")
public class XlsxImportController {
    private final UserRepository users;
    private final DeloRepository delos;
    private final TimeEntryRepository entries;
    private final ActivityMappingRepository mappings;
    private final XlsxImportRunRepository runs;
    private final XlsxImportQuestionRepository questions;

    public XlsxImportController(UserRepository users, DeloRepository delos, TimeEntryRepository entries,
                                ActivityMappingRepository mappings, XlsxImportRunRepository runs,
                                XlsxImportQuestionRepository questions) {
        this.users = users; this.delos = delos; this.entries = entries; this.mappings = mappings; this.runs = runs; this.questions = questions;
    }

    @PostMapping(value = "/xlsx", consumes = "multipart/form-data")
    @Transactional
    public ResponseEntity<ImportResponse> upload(Authentication auth, @RequestPart("file") MultipartFile file) throws Exception {
        User user = current(auth);
        String hash = sha256(file.getBytes());
        Optional<XlsxImportRun> existing = runs.findByUserAndFileHash(user, hash);
        if (existing.isPresent()) return ResponseEntity.ok(toResponse(existing.get()));
        XlsxImportRun run = runs.save(XlsxImportRun.builder().user(user).filename(file.getOriginalFilename() == null ? "import.xlsx" : file.getOriginalFilename())
                .fileHash(hash).status(XlsxImportRun.Status.DONE).build());
        int cells = 0, mapped = 0, unknown = 0;
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
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
        return ResponseEntity.ok(toResponse(run));
    }

    @GetMapping("/xlsx/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ImportResponse> get(Authentication auth, @PathVariable Long id) {
        User user = current(auth); XlsxImportRun run = runs.findById(id).filter(r -> r.getUser().getId().equals(user.getId())).orElseThrow();
        return ResponseEntity.ok(toResponse(run));
    }

    @PostMapping("/xlsx/{id}/resolve")
    @Transactional
    public ResponseEntity<ImportResponse> resolve(Authentication auth, @PathVariable Long id, @RequestBody ResolveRequest request) {
        User user = current(auth); XlsxImportRun run = runs.findById(id).filter(r -> r.getUser().getId().equals(user.getId())).orElseThrow();
        Delo delo;
        if (request.deloId() != null) delo = delos.findByUserAndId(user, request.deloId()).orElseThrow();
        else { delo = delos.save(Delo.builder().user(user).title(request.createDelo().title()).build()); }
        ActivityMapping mapping = mappings.findByUserAndActivityText(user, request.activityText()).orElseGet(() -> mappings.save(ActivityMapping.builder().user(user).activityText(request.activityText()).delo(delo).build()));
        for (XlsxImportQuestion q : questions.findByImportRunIdAndResolvedFalseOrderByStartAtAsc(id)) if (q.getActivityText().equals(request.activityText())) { entries.findByUserIdAndStartAt(user.getId(), q.getStartAt()).ifPresent(e -> { e.setDelo(mapping.getDelo()); e.setStatus(TimeEntry.Status.DONE); entries.save(e); }); q.setResolved(true); questions.save(q); }
        run.setPendingQuestions(questions.findByImportRunIdAndResolvedFalseOrderByStartAtAsc(id).size()); run.setStatus(run.getPendingQuestions() == 0 ? XlsxImportRun.Status.DONE : XlsxImportRun.Status.PAUSED); runs.save(run);
        return ResponseEntity.ok(toResponse(run));
    }

    private User current(Authentication a) { return users.findByUsername(a.getName()).orElseThrow(); }
    private static String sha256(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static LocalDate parseWeek(String name) { try { return LocalDate.parse(name.replaceAll(".*?(\\d{4}-\\d{2}-\\d{2}).*", "$1")); } catch (Exception e) { return LocalDate.of(2026, 4, 6); } }
    private ImportResponse toResponse(XlsxImportRun r) { return new ImportResponse(r.getId(), r.getStatus().name(), r.getTotalCells(), r.getMapped(), r.getUnknown(), r.getPendingQuestions()); }
    public record ImportResponse(Long id, String status, int totalCells, int mapped, int unknown, int pendingQuestions) {}
    public record ResolveRequest(String activityText, Long deloId, CreateDelo createDelo) {}
    public record CreateDelo(String title, Long projectId) {}
}
