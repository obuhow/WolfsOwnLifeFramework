/*
 * WOLF — Wolf's Own Life Framework
 * Copyright (C) 2025 Pavel Obukhov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package ru.wolf.api.datasync;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.wolf.api.datasync.dto.ApplyRequest;

@RestController
@RequestMapping("/api/v1/data-sync/import")
@RequiredArgsConstructor
public class DataSyncImportController {
    private final DataSyncControllerService controllerService;

    @PostMapping("/preview")
    public ResponseEntity<DataSyncImportService.PreviewResponse> preview(
            Authentication authentication,
            @RequestPart("file") MultipartFile file) throws Exception {
        return ResponseEntity.ok(controllerService.preview(authentication.getName(), file));
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<DataSyncImportService.PreviewResponse> getPreview(
            Authentication authentication, @PathVariable Long id) throws Exception {
        return ResponseEntity.ok(controllerService.getPreview(authentication.getName(), id));
    }

    @PostMapping("/{id}/apply")
    public ResponseEntity<DataSyncImportApplyService.ApplyResponse> apply(
            Authentication authentication, @PathVariable Long id, @RequestBody ApplyRequest request) throws Exception {
        return ResponseEntity.ok(controllerService.apply(authentication.getName(), id, request));
    }

    @GetMapping("/{id}/result")
    public ResponseEntity<DataSyncImportApplyService.ApplyResponse> result(
            Authentication authentication, @PathVariable Long id) throws Exception {
        return ResponseEntity.ok(controllerService.result(authentication.getName(), id));
    }

    @GetMapping("/{id}/plan")
    public ResponseEntity<DataSyncImportService.PreviewResponse> plan(
            Authentication authentication, @PathVariable Long id) throws Exception {
        return ResponseEntity.ok(controllerService.plan(authentication.getName(), id));
    }
}
