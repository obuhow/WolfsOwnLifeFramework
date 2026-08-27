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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.note.dto.NoteRequest;
import ru.wolf.api.note.dto.NoteResponse;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;
    private final ProjectRepository projectRepository;
    private final DeloRepository deloRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<NoteResponse> list(
            String username,
            Long projectId,
            Long deloId,
            Note.Author author,
            String tag,
            String query
    ) {
        User user = currentUser(username);
        String normalizedTag = normalize(tag);
        String normalizedQuery = normalize(query);
        String authorValue = author == null ? null : author.name();
        return noteRepository.search(user, projectId, deloId, authorValue, normalizedTag, normalizedQuery)
                .stream()
                .map(NoteResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public NoteResponse get(String username, Long id) {
        return NoteResponse.from(findNote(currentUser(username), id));
    }

    @Transactional
    public NoteResponse create(String username, NoteRequest request) {
        User user = currentUser(username);
        Parent parent = resolveParent(user, request.projectId(), request.deloId());
        Note note = Note.builder()
                .user(user)
                .project(parent.project())
                .delo(parent.delo())
                .author(request.author() == null ? Note.Author.USER : request.author())
                .body(request.body().trim())
                .tags(normalizeTags(request.tags()))
                .build();
        return NoteResponse.from(noteRepository.save(note));
    }

    @Transactional
    public NoteResponse update(String username, Long id, NoteRequest request) {
        User user = currentUser(username);
        Note note = findNote(user, id);
        Parent parent = resolveParent(user, request.projectId(), request.deloId());
        note.setProject(parent.project());
        note.setDelo(parent.delo());
        if (request.author() != null && request.author() != note.getAuthor()) {
            throw new IllegalArgumentException("Автор заметки не изменяется после создания");
        }
        note.setBody(request.body().trim());
        note.setTags(normalizeTags(request.tags()));
        return NoteResponse.from(noteRepository.save(note));
    }

    @Transactional
    public void delete(String username, Long id) {
        noteRepository.delete(findNote(currentUser(username), id));
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private Note findNote(User user, Long id) {
        return noteRepository.findByUserAndId(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Заметка не найдена"));
    }

    private record Parent(Project project, Delo delo) {
    }

    private Parent resolveParent(User user, Long projectId, Long deloId) {
        if ((projectId == null) == (deloId == null)) {
            throw new IllegalArgumentException("Заметка должна быть привязана ровно к одному Проекту или Делу");
        }
        if (projectId != null) {
            Project project = projectRepository.findByUserAndId(user, projectId)
                    .orElseThrow(() -> new IllegalArgumentException("Проект не найден"));
            return new Parent(project, null);
        }
        Delo delo = deloRepository.findByUserAndId(user, deloId)
                .orElseThrow(() -> new IllegalArgumentException("Дело не найдено"));
        return new Parent(null, delo);
    }

    private String[] normalizeTags(List<String> rawTags) {
        if (rawTags == null) return new String[0];
        return rawTags.stream()
                .map(this::normalize)
                .filter(value -> value != null)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .distinct()
                .toArray(String[]::new);
    }

    private String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
