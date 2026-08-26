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


import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.wolf.api.note.Note;
import ru.wolf.api.note.NoteAttachment;
import ru.wolf.api.note.NoteController;
import ru.wolf.api.note.NoteRepository;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

@RestController
@RequestMapping("/api/v1/notes")
public class NotesAssistantController {

    private final NotesAssistantProperties properties;
    private final NotesAssistantService assistantService;
    private final NoteRepository noteRepository;
    private final ProjectRepository projectRepository;
    private final DeloRepository deloRepository;
    private final UserRepository userRepository;

    public NotesAssistantController(
            NotesAssistantProperties properties,
            NotesAssistantService assistantService,
            NoteRepository noteRepository,
            ProjectRepository projectRepository,
            DeloRepository deloRepository,
            UserRepository userRepository
    ) {
        this.properties = properties;
        this.assistantService = assistantService;
        this.noteRepository = noteRepository;
        this.projectRepository = projectRepository;
        this.deloRepository = deloRepository;
        this.userRepository = userRepository;
    }

    @PostMapping(value = "/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<NoteController.NoteResponse> audio(
            Authentication authentication,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long deloId,
            @RequestParam(value = "tags", required = false) List<String> tags
    ) {
        requireEnabled();
        User user = currentUser(authentication);
        if ((projectId == null) == (deloId == null)) {
            throw new IllegalArgumentException("Аудиозаметка должна быть привязана ровно к одному Проекту или Делу");
        }
        Project project = projectId == null ? null : projectRepository.findByUserAndId(user, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Проект не найден"));
        Delo delo = deloId == null ? null : deloRepository.findByUserAndId(user, deloId)
                .orElseThrow(() -> new IllegalArgumentException("Дело не найдено"));
        NotesAssistantService.StoredAudio audio = assistantService.store(file);
        Note note = Note.builder()
                .user(user)
                .project(project)
                .delo(delo)
                .author(Note.Author.USER)
                .body(assistantService.transcribe(audio.audioRef()))
                .tags(normalizeTags(tags))
                .build();
        note.setAudioAttachment(NoteAttachment.builder()
                .note(note)
                .audioRef(audio.audioRef())
                .contentType(audio.contentType())
                .originalFilename(audio.originalFilename())
                .build());
        return ResponseEntity.ok(toResponse(noteRepository.save(note)));
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new LlmDisabledException("LLM-функции отключены: установите wolf.llm.enabled=true");
        }
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private String[] normalizeTags(List<String> rawTags) {
        if (rawTags == null) {
            return new String[0];
        }
        return rawTags.stream().flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim).filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT)).distinct().toArray(String[]::new);
    }

    private NoteController.NoteResponse toResponse(Note note) {
        return new NoteController.NoteResponse(
                note.getId(),
                note.getProject() == null ? null : note.getProject().getId(),
                note.getProject() == null ? null : note.getProject().getTitle(),
                note.getDelo() == null ? null : note.getDelo().getId(),
                note.getDelo() == null ? null : note.getDelo().getTitle(),
                note.getAuthor(), note.getBody(), Arrays.asList(note.getTags()),
                note.getAudioAttachment().getAudioRef(), note.getCreatedAt(), note.getUpdatedAt()
        );
    }

    public static class LlmDisabledException extends RuntimeException {
        public LlmDisabledException(String message) {
            super(message);
        }
    }
}
