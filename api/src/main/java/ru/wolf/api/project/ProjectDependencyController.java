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
import ru.wolf.api.project.dto.AddDependencyRequest;
import ru.wolf.api.project.dto.DependenciesResponse;

@RestController
@RequestMapping("/api/v1/projects/{id}/dependencies")
@RequiredArgsConstructor
public class ProjectDependencyController {

    private final ProjectDependencyService dependencyService;

    @GetMapping
    public ResponseEntity<DependenciesResponse> list(
            Authentication authentication,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(dependencyService.list(authentication.getName(), id));
    }

    @PostMapping
    public ResponseEntity<DependenciesResponse> add(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody AddDependencyRequest request
    ) {
        return ResponseEntity.ok(dependencyService.add(authentication.getName(), id, request));
    }

    @DeleteMapping("/{blockerId}")
    public ResponseEntity<Void> remove(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long blockerId
    ) {
        dependencyService.remove(authentication.getName(), id, blockerId);
        return ResponseEntity.noContent().build();
    }
}
