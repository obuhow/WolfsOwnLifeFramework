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

@RestController
@RequestMapping("/api/v1/import")
@RequiredArgsConstructor
public class XlsxImportController {
    private final XlsxImportService service;

    @PostMapping(value = "/xlsx", consumes = "multipart/form-data")
    public ResponseEntity<ImportResponse> upload(Authentication auth, @RequestPart("file") MultipartFile file)
            throws Exception {
        return ResponseEntity.ok(service.upload(auth.getName(), file.getBytes(), file.getOriginalFilename()));
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
