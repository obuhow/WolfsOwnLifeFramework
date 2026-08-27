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
import ru.wolf.api.delo.DeloService.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/delos")
@RequiredArgsConstructor
public class DeloController {
    private final DeloService service;

    @GetMapping
    public ResponseEntity<List<DeloService.DeloResponse>> listDelos(Authentication authentication) {
        return ResponseEntity.ok(service.listDelos(authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeloService.DeloDetailResponse> getDelo(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(service.getDelo(authentication.getName(), id));
    }

    @PostMapping
    public ResponseEntity<DeloService.DeloResponse> createDelo(Authentication authentication,
                                                    @Valid @RequestBody CreateDeloRequest request) {
        return ResponseEntity.ok(service.createDelo(authentication.getName(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DeloService.DeloResponse> updateDelo(Authentication authentication, @PathVariable Long id,
                                                    @Valid @RequestBody UpdateDeloRequest request) {
        return ResponseEntity.ok(service.updateDelo(authentication.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDelo(Authentication authentication, @PathVariable Long id) {
        service.deleteDelo(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{deloId}/link/{projectId}")
    public ResponseEntity<DeloService.DeloResponse> linkProject(Authentication authentication, @PathVariable Long deloId,
                                                     @PathVariable Long projectId) {
        return ResponseEntity.ok(service.linkProject(authentication.getName(), deloId, projectId));
    }

    @DeleteMapping("/{deloId}/link/{projectId}")
    public ResponseEntity<DeloService.DeloResponse> unlinkProject(Authentication authentication, @PathVariable Long deloId,
                                                       @PathVariable Long projectId) {
        return ResponseEntity.ok(service.unlinkProject(authentication.getName(), deloId, projectId));
    }

    @PutMapping("/{deloId}/primary/{projectId}")
    public ResponseEntity<DeloService.DeloResponse> setPrimaryProject(Authentication authentication, @PathVariable Long deloId,
                                                          @PathVariable Long projectId) {
        return ResponseEntity.ok(service.setPrimaryProject(authentication.getName(), deloId, projectId));
    }

    @PostMapping("/{id}/apply-recurrence")
    public ResponseEntity<DeloService.ApplyRecurrenceResponse> applyRecurrence(Authentication authentication, @PathVariable Long id,
                                                                    @RequestBody(required = false) ApplyRecurrenceRequest request) {
        return ResponseEntity.ok(service.applyRecurrence(authentication.getName(), id, request));
    }


    /** Compatibility DTO names retained for existing API integration tests. */
    @Deprecated
    public static class DeloResponse extends DeloService.DeloResponse {
        public DeloResponse() {
            super();
        }

        public DeloResponse(Long id, String title, String description, Delo.ExecutionMode executionMode,
                            List<Long> projectIds, Long primaryProjectId) {
            super(id, title, description, executionMode, projectIds, primaryProjectId);
        }
    }

    @Deprecated
    public static class DeloDetailResponse extends DeloService.DeloDetailResponse {
        public DeloDetailResponse() {
            super();
        }
    }

    @Deprecated
    public static class CreateDeloRequest extends DeloService.CreateDeloRequest {
        public CreateDeloRequest() {
            super();
        }

        public CreateDeloRequest(String title, String description, Delo.ExecutionMode executionMode,
                                 List<Long> projectIds, Long primaryProjectId) {
            super();
            setTitle(title);
            setDescription(description);
            setExecutionMode(executionMode);
            setProjectIds(projectIds);
            setPrimaryProjectId(primaryProjectId);
        }
    }

    @Deprecated
    public static class RecurrenceSlotDto extends DeloService.RecurrenceSlotDto {
        public RecurrenceSlotDto() {
            super();
        }
    }

    @Deprecated
    public static class ApplyRecurrenceResponse extends DeloService.ApplyRecurrenceResponse {
        public ApplyRecurrenceResponse() {
            super();
        }
    }

    @Deprecated
    public static class UpdateDeloRequest extends DeloService.UpdateDeloRequest {
        public UpdateDeloRequest() {
            super();
        }
    }

    @Deprecated
    public static class DeloImportResponse extends DeloImportService.ImportResponse {
        public DeloImportResponse(int imported, boolean addedToCurrentWeek) {
            super(imported, addedToCurrentWeek);
        }
    }

}
