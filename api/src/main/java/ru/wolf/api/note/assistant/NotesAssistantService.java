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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.note.Note;
import ru.wolf.api.note.NoteAttachment;
import ru.wolf.api.note.NoteRepository;
import ru.wolf.api.note.dto.NoteResponse;
import ru.wolf.api.note.assistant.dto.ResumeResponse;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

@Service
@RequiredArgsConstructor
public class NotesAssistantService {

    private final AssistantPort assistant;
    private final NotesAssistantProperties properties;
    private final NoteRepository noteRepository;
    private final ProjectRepository projectRepository;
    private final DeloRepository deloRepository;
    private final UserRepository userRepository;

    @Transactional
    public NoteResponse createAudioNote(
            String username,
            MultipartFile file,
            Long projectId,
            Long deloId,
            List<String> tags
    ) {
        User user = currentUser(username);
        if ((projectId == null) == (deloId == null)) {
            throw new IllegalArgumentException("Аудиозаметка должна быть привязана ровно к одному Проекту или Делу");
        }
        Project project = projectId == null ? null : projectRepository.findByUserAndId(user, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Проект не найден"));
        Delo delo = deloId == null ? null : deloRepository.findByUserAndId(user, deloId)
                .orElseThrow(() -> new IllegalArgumentException("Дело не найдено"));
        StoredAudio audio = store(file);
        Note note = Note.builder()
                .user(user)
                .project(project)
                .delo(delo)
                .author(Note.Author.USER)
                .body(assistant.transcribe(audio.audioRef()))
                .tags(normalizeTags(tags))
                .build();
        note.setAudioAttachment(NoteAttachment.builder()
                .note(note)
                .audioRef(audio.audioRef())
                .contentType(audio.contentType())
                .originalFilename(audio.originalFilename())
                .build());
        return NoteResponse.from(noteRepository.save(note));
    }

    @Transactional(readOnly = true)
    public ResumeResponse resume(String username, Long projectId, int limit) {
        User user = currentUser(username);
        Project project = projectRepository.findByUserAndId(user, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Проект не найден"));
        List<Note> notes = noteRepository.findByUserAndProjectIdOrderByCreatedAtDesc(
                user, project.getId(), PageRequest.of(0, limit));
        List<Long> noteIds = notes.stream().map(Note::getId).toList();
        return new ResumeResponse(
                project.getId(), project.getTitle(), noteIds, assistant.summarize(project.getId(), noteIds));
    }

    public StoredAudio store(MultipartFile file) {
        String safeName = UUID.randomUUID() + "-" + sanitizeFilename(file.getOriginalFilename());
        Path directory = Paths.get(properties.getAudioDirectory());
        try {
            Files.createDirectories(directory);
            Path target = directory.resolve(safeName).normalize();
            if (!target.getParent().equals(directory.toAbsolutePath().normalize())) {
                throw new IllegalArgumentException("Недопустимое имя аудиофайла");
            }
            file.transferTo(target);
            return new StoredAudio(target.toString(), file.getContentType(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сохранить аудиофайл", e);
        }
    }

    public String transcribe(String audioRef) {
        return assistant.transcribe(audioRef);
    }

    public String summarize(Long projectId, List<Long> noteIds) {
        return assistant.summarize(projectId, noteIds);
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private String[] normalizeTags(List<String> rawTags) {
        if (rawTags == null) {
            return new String[0];
        }
        return rawTags.stream()
                .flatMap(value -> java.util.Arrays.stream(value.split(",")))
                .map(String::trim).filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .distinct()
                .toArray(String[]::new);
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "audio.bin";
        }
        return Path.of(filename).getFileName().toString().replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public record StoredAudio(String audioRef, String contentType, String originalFilename) {
    }
}
