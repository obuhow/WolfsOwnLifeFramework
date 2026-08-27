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
package ru.wolf.api.note;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.note.dto.NoteRequest;
import ru.wolf.api.note.dto.NoteResponse;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @GetMapping
    public ResponseEntity<List<NoteResponse>> list(
            Authentication authentication,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long deloId,
            @RequestParam(required = false) Note.Author author,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String q
    ) {
        return ResponseEntity.ok(
                noteService.list(authentication.getName(), projectId, deloId, author, tag, q));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NoteResponse> get(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(noteService.get(authentication.getName(), id));
    }

    @PostMapping
    public ResponseEntity<NoteResponse> create(
            Authentication authentication,
            @Valid @RequestBody NoteRequest request
    ) {
        return ResponseEntity.ok(noteService.create(authentication.getName(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NoteResponse> update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody NoteRequest request
    ) {
        return ResponseEntity.ok(noteService.update(authentication.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        noteService.delete(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
