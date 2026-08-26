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

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.note.Note;
import ru.wolf.api.note.NoteRepository;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/resume")
public class ProjectResumeController {

    private final NotesAssistantProperties properties;
    private final NotesAssistantService assistantService;
    private final NoteRepository noteRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public ProjectResumeController(
            NotesAssistantProperties properties,
            NotesAssistantService assistantService,
            NoteRepository noteRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository
    ) {
        this.properties = properties;
        this.assistantService = assistantService;
        this.noteRepository = noteRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ResumeResponse> resume(
            Authentication authentication,
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        if (!properties.isEnabled()) {
            throw new NotesAssistantController.LlmDisabledException(
                    "LLM-функции отключены: установите wolf.llm.enabled=true");
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Параметр limit должен быть от 1 до 100");
        }
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
        Project project = projectRepository.findByUserAndId(user, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Проект не найден"));
        List<Note> notes = noteRepository.findByUserAndProjectIdOrderByCreatedAtDesc(
                user, project.getId(), PageRequest.of(0, limit));
        List<Long> noteIds = notes.stream().map(Note::getId).toList();
        return ResponseEntity.ok(new ResumeResponse(
                project.getId(), project.getTitle(), noteIds, assistantService.summarize(project.getId(), noteIds)));
    }

    public record ResumeResponse(Long projectId, String projectTitle, List<Long> noteIds, String summary) {
    }
}
