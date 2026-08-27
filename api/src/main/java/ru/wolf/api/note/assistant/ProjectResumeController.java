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

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.note.assistant.dto.ResumeResponse;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/resume")
@RequiredArgsConstructor
public class ProjectResumeController {

    private final NotesAssistantProperties properties;
    private final NotesAssistantService assistantService;

    @GetMapping
    public ResponseEntity<ResumeResponse> resume(
            Authentication authentication,
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        requireEnabled();
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Параметр limit должен быть от 1 до 100");
        }
        return ResponseEntity.ok(assistantService.resume(authentication.getName(), projectId, limit));
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new LlmDisabledException("LLM-функции отключены: установите wolf.llm.enabled=true");
        }
    }
}
