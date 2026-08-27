package ru.wolf.api.checklist;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.checklist.dto.*;
import java.time.LocalDate;
@RestController @RequestMapping("/api/v1/reports/checklist") @RequiredArgsConstructor
public class ChecklistReportController {
 private final ChecklistReportService service;
 @GetMapping public ReportResponse report(Authentication a,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to){return service.report(a,from,to);}
 @GetMapping("/export") public ResponseEntity<ByteArrayResource> export(Authentication a,@RequestParam(defaultValue="md") String format,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to){return service.export(a,format,from,to);}
}
