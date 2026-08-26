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
        User user = currentUser(authentication);
        byte[] workbook = exportService.export(user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("wolf-data-0.21.xlsx").build().toString())
                .contentLength(workbook.length)
                .body(new ByteArrayResource(workbook));
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }
}
