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

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.wolf.api.importxlsx.XlsxImportService.*;
import ru.wolf.api.importxlsx.dto.ImportApplyResponse;
import ru.wolf.api.importxlsx.dto.ImportPreviewResponse;

/**
 * Release 1.4 ticket 02: the schedule import is now two explicit steps — preview, then apply.
 *
 * <p>Before this ticket {@code POST /import/xlsx} wrote Записи времени the moment a file was
 * uploaded, so the user learned what the system had decided only after it had decided it. Now the
 * upload answers "what WOULD this create" and writes nothing (decision И-I); the writing happens
 * only on the explicit «Применить».
 */
@RestController
@RequestMapping("/api/v1/import")
@RequiredArgsConstructor
public class XlsxImportController {
    private final XlsxImportService service;
    private final XlsxSchedulePreviewService previewService;

    /**
     * Parses the uploaded file and returns what applying it would do. Writes nothing — no run, no
     * Записи времени, no questions.
     */
    @PostMapping(value = "/xlsx", consumes = "multipart/form-data")
    public ResponseEntity<ImportPreviewResponse> preview(Authentication auth, @RequestPart("file") MultipartFile file)
            throws Exception {
        return ResponseEntity.ok(previewService.preview(auth.getName(), file.getBytes()));
    }

    /** The explicit second step: materialise the previewed cells as Записи времени. */
    @PostMapping(value = "/xlsx/apply", consumes = "multipart/form-data")
    public ResponseEntity<ImportApplyResponse> apply(Authentication auth, @RequestPart("file") MultipartFile file,
                                                      @RequestParam(value = "conflictStrategy",
                                                              defaultValue = "SKIP_ALL") ImportConflictStrategy conflictStrategy)
            throws Exception {
        return ResponseEntity.ok(previewService.apply(auth.getName(), file.getBytes(), file.getOriginalFilename(), conflictStrategy));
    }

    @GetMapping("/xlsx/{id}")
    public ResponseEntity<ImportResponse> get(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(service.get(auth.getName(), id));
    }

    @PostMapping("/xlsx/{id}/resolve")
    public ResponseEntity<ImportResponse> resolve(Authentication auth, @PathVariable Long id,
                                                   @RequestBody ResolveRequest request) {
        return ResponseEntity.ok(service.resolve(auth.getName(), id, request));
    }
}
