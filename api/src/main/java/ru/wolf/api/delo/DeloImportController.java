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
package ru.wolf.api.delo;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.wolf.api.delo.dto.ImportResponse;

@RestController
@RequestMapping("/api/v1/delos")
@RequiredArgsConstructor
public class DeloImportController {
    private final DeloImportService service;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResponse> importCsv(Authentication authentication,
                                                     @RequestParam("file") MultipartFile file,
                                                     @RequestParam(value = "addToCurrentWeek", defaultValue = "false") boolean addToCurrentWeek,
                                                     @RequestParam(value = "skipOverlapCheck", defaultValue = "false") boolean skipOverlapCheck) {
        try {
            return ResponseEntity.ok(service.importCsv(authentication.getName(), file.getBytes(), addToCurrentWeek, skipOverlapCheck));
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("Не удалось прочитать CSV-файл", ex);
        }
    }
}
