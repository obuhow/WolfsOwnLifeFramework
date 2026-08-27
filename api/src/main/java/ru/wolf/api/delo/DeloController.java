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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.delo.dto.ApplyRecurrenceRequest;
import ru.wolf.api.delo.dto.ApplyRecurrenceResponse;
import ru.wolf.api.delo.dto.CreateDeloRequest;
import ru.wolf.api.delo.dto.DeloDetailResponse;
import ru.wolf.api.delo.dto.DeloResponse;
import ru.wolf.api.delo.dto.UpdateDeloRequest;

import java.util.List;

@RestController
@RequestMapping("/api/v1/delos")
@RequiredArgsConstructor
public class DeloController {
    private final DeloService service;

    @GetMapping
    public ResponseEntity<List<DeloResponse>> listDelos(Authentication authentication) {
        return ResponseEntity.ok(service.listDelos(authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeloDetailResponse> getDelo(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(service.getDelo(authentication.getName(), id));
    }

    @PostMapping
    public ResponseEntity<DeloResponse> createDelo(Authentication authentication,
                                                    @Valid @RequestBody CreateDeloRequest request) {
        return ResponseEntity.ok(service.createDelo(authentication.getName(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DeloResponse> updateDelo(Authentication authentication, @PathVariable Long id,
                                                    @Valid @RequestBody UpdateDeloRequest request) {
        return ResponseEntity.ok(service.updateDelo(authentication.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDelo(Authentication authentication, @PathVariable Long id) {
        service.deleteDelo(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{deloId}/link/{projectId}")
    public ResponseEntity<DeloResponse> linkProject(Authentication authentication, @PathVariable Long deloId,
                                                     @PathVariable Long projectId) {
        return ResponseEntity.ok(service.linkProject(authentication.getName(), deloId, projectId));
    }

    @DeleteMapping("/{deloId}/link/{projectId}")
    public ResponseEntity<DeloResponse> unlinkProject(Authentication authentication, @PathVariable Long deloId,
                                                       @PathVariable Long projectId) {
        return ResponseEntity.ok(service.unlinkProject(authentication.getName(), deloId, projectId));
    }

    @PutMapping("/{deloId}/primary/{projectId}")
    public ResponseEntity<DeloResponse> setPrimaryProject(Authentication authentication, @PathVariable Long deloId,
                                                          @PathVariable Long projectId) {
        return ResponseEntity.ok(service.setPrimaryProject(authentication.getName(), deloId, projectId));
    }

    @PostMapping("/{id}/apply-recurrence")
    public ResponseEntity<ApplyRecurrenceResponse> applyRecurrence(Authentication authentication, @PathVariable Long id,
                                                                    @RequestBody(required = false) ApplyRecurrenceRequest request) {
        return ResponseEntity.ok(service.applyRecurrence(authentication.getName(), id, request));
    }

}
