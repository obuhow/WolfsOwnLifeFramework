package ru.wolf.api.note;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteRepository noteRepository;
    private final ProjectRepository projectRepository;
    private final DeloRepository deloRepository;
    private final UserRepository userRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<NoteResponse>> list(
            Authentication authentication,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long deloId,
            @RequestParam(required = false) Note.Author author,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String q
    ) {
        User user = currentUser(authentication);
        String normalizedTag = normalize(tag);
        String normalizedQuery = normalize(q);
        String authorValue = author == null ? null : author.name();
        return ResponseEntity.ok(noteRepository.search(user, projectId, deloId, authorValue, normalizedTag, normalizedQuery)
                .stream()
                .map(this::toResponse)
                .toList());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<NoteResponse> get(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(toResponse(findNote(currentUser(authentication), id)));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<NoteResponse> create(
            Authentication authentication,
            @Valid @RequestBody NoteRequest request
    ) {
        User user = currentUser(authentication);
        Parent parent = resolveParent(user, request.getProjectId(), request.getDeloId());
        Note note = Note.builder()
                .user(user)
                .project(parent.project())
                .delo(parent.delo())
                .author(request.getAuthor() == null ? Note.Author.USER : request.getAuthor())
                .body(request.getBody().trim())
                .tags(normalizeTags(request.getTags()))
                .build();
        return ResponseEntity.ok(toResponse(noteRepository.save(note)));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<NoteResponse> update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody NoteRequest request
    ) {
        User user = currentUser(authentication);
        Note note = findNote(user, id);
        Parent parent = resolveParent(user, request.getProjectId(), request.getDeloId());
        note.setProject(parent.project());
        note.setDelo(parent.delo());
        if (request.getAuthor() != null && request.getAuthor() != note.getAuthor()) {
            throw new IllegalArgumentException("Автор заметки не изменяется после создания");
        }
        note.setBody(request.getBody().trim());
        note.setTags(normalizeTags(request.getTags()));
        return ResponseEntity.ok(toResponse(noteRepository.save(note)));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        noteRepository.delete(findNote(currentUser(authentication), id));
        return ResponseEntity.noContent().build();
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private Note findNote(User user, Long id) {
        return noteRepository.findByUserAndId(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Заметка не найдена"));
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

    private NoteResponse toResponse(Note note) {
        return new NoteResponse(
                note.getId(),
                note.getProject() == null ? null : note.getProject().getId(),
                note.getProject() == null ? null : note.getProject().getTitle(),
                note.getDelo() == null ? null : note.getDelo().getId(),
                note.getDelo() == null ? null : note.getDelo().getTitle(),
                note.getAuthor(),
                note.getBody(),
                Arrays.asList(note.getTags()),
                note.getAudioAttachment() == null ? null : note.getAudioAttachment().getAudioRef(),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }

    private record Parent(Project project, Delo delo) {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NoteRequest {
        private Long projectId;
        private Long deloId;
        private Note.Author author;

        @NotBlank
        @Size(max = 100000)
        private String body;

        @Size(max = 50)
        private List<@NotBlank @Size(max = 100) String> tags;
    }

    @Data
    @AllArgsConstructor
    public static class NoteResponse {
        private Long id;
        private Long projectId;
        private String projectTitle;
        private Long deloId;
        private String deloTitle;
        private Note.Author author;
        private String body;
        private List<String> tags;
        private String audioRef;
        private java.time.Instant createdAt;
        private java.time.Instant updatedAt;
    }
}
