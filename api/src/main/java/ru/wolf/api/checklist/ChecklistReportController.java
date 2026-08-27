package ru.wolf.api.checklist;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import ru.wolf.api.checklist.dto.*;

@RestController @RequestMapping("/api/v1/reports/checklist") @RequiredArgsConstructor
public class ChecklistReportController {
    private final ChecklistReportService service;

    @GetMapping
    public ReportResponse report(Authentication a, @RequestParam(required=false) LocalDate from,
                                 @RequestParam(required=false) LocalDate to) {
        return service.report(a.getName(), from, to);
    }

    @GetMapping("/export")
    public ResponseEntity<ByteArrayResource> export(Authentication a,
            @RequestParam(defaultValue="md") String format,
            @RequestParam(required=false) LocalDate from,
            @RequestParam(required=false) LocalDate to) {
        ChecklistReportService.ExportResult result = service.export(a.getName(), format, from, to);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + result.filename())
                .body(new ByteArrayResource(result.content()));
    }
}
