package ru.wolf.api.datasync;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
            Authentication authentication,
            @RequestPart("file") MultipartFile file) throws Exception {
        return ResponseEntity.ok(importService.preview(currentUser(authentication), file));
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<DataSyncImportService.PreviewResponse> getPreview(
            Authentication authentication, @PathVariable Long id) throws Exception {
        return ResponseEntity.ok(importService.get(currentUser(authentication), id));
    }

    @PostMapping("/{id}/apply")
    public ResponseEntity<DataSyncImportApplyService.ApplyResponse> apply(
            Authentication authentication, @PathVariable Long id, @RequestBody ApplyRequest request) throws Exception {
        return ResponseEntity.ok(applyService.apply(currentUser(authentication), id, request.checksum(), request.deleteMissing(), request.scopes()));
    }

    @GetMapping("/{id}/result")
    public ResponseEntity<DataSyncImportApplyService.ApplyResponse> result(
            Authentication authentication, @PathVariable Long id) throws Exception {
        SyncImportPreview preview = importService.find(currentUser(authentication), id);
        if (preview.getResultJson() == null) throw new IllegalArgumentException("Preview ещё не применён");
        return ResponseEntity.ok(objectMapper.readValue(
                preview.getResultJson(), DataSyncImportApplyService.ApplyResponse.class));
    }

    @GetMapping("/{id}/plan")
    public ResponseEntity<DataSyncImportService.PreviewResponse> plan(
            Authentication authentication, @PathVariable Long id) throws Exception {
        return ResponseEntity.ok(importService.get(currentUser(authentication), id));
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    public record ApplyRequest(String checksum, boolean deleteMissing, java.util.List<String> scopes) { }
}
