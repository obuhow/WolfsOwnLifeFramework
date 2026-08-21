package ru.wolf.api.datasync;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

@RestController
@RequestMapping("/api/v1/data-sync/import")
@RequiredArgsConstructor
public class DataSyncImportController {
    private final DataSyncImportService importService;
    private final DataSyncImportApplyService applyService;
    private final UserRepository userRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @PostMapping("/preview")
    public ResponseEntity<DataSyncImportService.PreviewResponse> preview(
            @RequestPart("file") MultipartFile file) throws Exception {
        User user = userRepository.findByUsername("admin")
                .orElseThrow(() -> new IllegalStateException("Default user not found"));
        return ResponseEntity.ok(importService.preview(user, file));
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<DataSyncImportService.PreviewResponse> getPreview(
            @PathVariable Long id) throws Exception {
        return ResponseEntity.ok(importService.get(defaultUser(), id));
    }

    @PostMapping("/{id}/apply")
    public ResponseEntity<DataSyncImportApplyService.ApplyResponse> apply(
            @PathVariable Long id, @RequestBody ApplyRequest request) throws Exception {
        return ResponseEntity.ok(applyService.apply(defaultUser(), id, request.checksum(), request.deleteMissing(), request.scopes()));
    }

    @GetMapping("/{id}/result")
    public ResponseEntity<DataSyncImportApplyService.ApplyResponse> result(
            @PathVariable Long id) throws Exception {
        SyncImportPreview preview = importService.find(defaultUser(), id);
        if (preview.getResultJson() == null) throw new IllegalArgumentException("Preview ещё не применён");
        return ResponseEntity.ok(objectMapper.readValue(
                preview.getResultJson(), DataSyncImportApplyService.ApplyResponse.class));
    }

    private User defaultUser() {
        return userRepository.findByUsername("admin")
                .orElseThrow(() -> new IllegalStateException("Default user not found"));
    }

    public record ApplyRequest(String checksum, boolean deleteMissing, java.util.List<String> scopes) { }

    @GetMapping("/{id}/plan")
    public ResponseEntity<DataSyncImportService.PreviewResponse> plan(
            @PathVariable Long id) throws Exception {
        return ResponseEntity.ok(importService.get(defaultUser(), id));
    }
}
