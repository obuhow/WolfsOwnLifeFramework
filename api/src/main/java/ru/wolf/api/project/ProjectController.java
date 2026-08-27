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
 * along with this program. If not see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.project;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.project.dto.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> listProjects(
            Authentication authentication,
            @RequestParam(required = false) Long lifeAreaId
    ) {
        return ResponseEntity.ok(projectService.listProjects(authentication.getName(), lifeAreaId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectDetailResponse> getProject(
            Authentication authentication,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(projectService.getProject(authentication.getName(), id));
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            Authentication authentication,
            @Valid @RequestBody CreateProjectRequest request
    ) {
        return ResponseEntity.ok(projectService.createProject(authentication.getName(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponse> updateProject(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateProjectRequest request
    ) {
        return ResponseEntity.ok(projectService.updateProject(authentication.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(
            Authentication authentication,
            @PathVariable Long id
    ) {
        projectService.deleteProject(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/plan-shift-preview")
    public ResponseEntity<ResourceCascadeService.Preview> planShiftPreview(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody PlanShiftPreviewRequest request
    ) {
        return ResponseEntity.ok(projectService.planShiftPreview(authentication.getName(), id, request));
    }

    @PostMapping("/{id}/plan-distribution")
    public ResponseEntity<PlanDistributionService.DistributionResult> applyPlanDistribution(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody PlanDistributionRequest request) {
        return ResponseEntity.ok(projectService.applyPlanDistribution(authentication.getName(), id, request));
    }
}
