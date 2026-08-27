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
package ru.wolf.api.backlog;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.backlog.dto.MoveRequest;
import ru.wolf.api.backlog.dto.Request;
import ru.wolf.api.backlog.dto.Response;

import java.util.List;

@RestController
@RequestMapping("/api/v1/backlog")
@RequiredArgsConstructor
public class BacklogController {

    private final BacklogService backlogService;

    @GetMapping
    public List<Response> list(Authentication auth, @RequestParam String scope, @RequestParam String period) {
        return backlogService.list(auth.getName(), scope, period);
    }

    @PostMapping
    public ResponseEntity<Response> create(Authentication auth, @Valid @RequestBody Request request) {
        return backlogService.create(auth.getName(), request);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Response> update(Authentication auth, @PathVariable Long id, @RequestBody Request request) {
        return ResponseEntity.ok(backlogService.update(auth.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable Long id) {
        backlogService.delete(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/move-to-week")
    public ResponseEntity<Response> moveToWeek(Authentication auth, @PathVariable Long id, @RequestBody MoveRequest request) {
        return ResponseEntity.ok(backlogService.moveToWeek(auth.getName(), id, request));
    }
}
