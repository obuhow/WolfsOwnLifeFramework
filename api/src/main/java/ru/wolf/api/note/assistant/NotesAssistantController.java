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
package ru.wolf.api.note.assistant;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.wolf.api.note.dto.NoteResponse;

@RestController
@RequestMapping("/api/v1/notes")
@RequiredArgsConstructor
public class NotesAssistantController {

    private final NotesAssistantProperties properties;
    private final NotesAssistantService assistantService;

    @PostMapping(value = "/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<NoteResponse> audio(
            Authentication authentication,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long deloId,
            @RequestParam(value = "tags", required = false) List<String> tags
    ) {
        requireEnabled();
        return ResponseEntity.ok(assistantService.createAudioNote(
                authentication.getName(), file, projectId, deloId, tags));
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new LlmDisabledException("LLM-функции отключены: установите wolf.llm.enabled=true");
        }
    }
}
