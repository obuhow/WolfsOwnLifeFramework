package ru.wolf.api.datasync;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

@RestController
@RequestMapping("/api/v1/data-sync")
@RequiredArgsConstructor
public class DataSyncExportController {
    private final DataSyncExportService exportService;
    private final UserRepository userRepository;

    @GetMapping("/export")
    public ResponseEntity<ByteArrayResource> export(
            Authentication authentication,
            @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(defaultValue = DataSyncContract.VERSION) String version) throws Exception {
        if (!"xlsx".equalsIgnoreCase(format) || !DataSyncContract.VERSION.equals(version)) {
            throw new IllegalArgumentException("Only XLSX data-sync version 0.21 is supported");
        }
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
        byte[] workbook = exportService.export(user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("wolf-data-0.21.xlsx").build().toString())
                .contentLength(workbook.length)
                .body(new ByteArrayResource(workbook));
    }
}
