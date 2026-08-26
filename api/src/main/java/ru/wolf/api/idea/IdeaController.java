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
package ru.wolf.api.idea;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.idea.dto.CreateIdeaRequest;
import ru.wolf.api.idea.dto.IdeaResponse;
import ru.wolf.api.idea.dto.PromoteIdeaRequest;
import ru.wolf.api.idea.dto.PromoteResponse;
import ru.wolf.api.idea.dto.UpdateIdeaRequest;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ideas")
@RequiredArgsConstructor
public class IdeaController {

    private final IdeaService ideaService;

    @GetMapping
    public ResponseEntity<List<IdeaResponse>> list(
            Authentication authentication,
            @RequestParam(required = false) Idea.Category category,
            @RequestParam(required = false) Idea.Status status) {
        return ResponseEntity.ok(ideaService.list(authentication.getName(), category, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<IdeaResponse> get(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(ideaService.get(authentication.getName(), id));
    }

    @PostMapping
    public ResponseEntity<IdeaResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateIdeaRequest request) {
        return ResponseEntity.ok(ideaService.create(authentication.getName(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<IdeaResponse> update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateIdeaRequest request) {
        return ResponseEntity.ok(ideaService.update(authentication.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        ideaService.delete(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/promote")
    public ResponseEntity<PromoteResponse> promote(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody PromoteIdeaRequest request) {
        return ResponseEntity.ok(ideaService.promote(authentication.getName(), id, request));
    }
}
