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

@RestController
@RequestMapping("/api/v1/data-sync")
@RequiredArgsConstructor
public class DataSyncExportController {
    private final DataSyncControllerService controllerService;

    @GetMapping("/export")
    public ResponseEntity<ByteArrayResource> export(
            Authentication authentication,
            @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(defaultValue = DataSyncContract.VERSION) String version) throws Exception {
        byte[] payload = controllerService.export(authentication.getName(), format, version);
        boolean csv = "csv".equalsIgnoreCase(format);
        String mediaType = csv
                ? "text/csv; charset=utf-8"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        String filename = "wolf-data-" + DataSyncContract.VERSION + (csv ? ".csv" : ".xlsx");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mediaType))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename).build().toString())
                .contentLength(payload.length)
                .body(new ByteArrayResource(payload));
    }
}
