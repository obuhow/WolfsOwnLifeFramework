package ru.wolf.api.morning;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.wolf.api.idea.Idea;
import ru.wolf.api.note.Note;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/morning-digest")
@RequiredArgsConstructor
public class MorningDigestController {

    private final MorningDigestService morningDigestService;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<MorningDigestResponse> get(Authentication authentication) {
        return ResponseEntity.ok(morningDigestService.build(authentication.getName()));
    }

    @Data
    @AllArgsConstructor
    public static class MorningDigestResponse {
        private String weekId;
        private List<ProjectDigest> projects;
        private List<IdeaDigest> ideas;
        private List<GoalFactDigest> goalsFact;
    }

    @Data
    @AllArgsConstructor
    public static class ProjectDigest {
        private Long id;
        private String title;
        private List<NoteDigest> lastNotes;
        private List<DeloDigest> topDelos;
    }

    @Data
    @AllArgsConstructor
    public static class NoteDigest {
        private Long id;
        private Note.Author author;
        private String body;
        private String[] tags;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    @AllArgsConstructor
    public static class DeloDigest {
        private Long id;
        private String title;
    }

    @Data
    @AllArgsConstructor
    public static class IdeaDigest {
        private Long id;
        private String title;
        private String description;
        private Idea.Category category;
    }

    @Data
    @AllArgsConstructor
    public static class GoalFactDigest {
        private Long goalId;
        private String title;
        private BigDecimal budgetHours;
        private BigDecimal factHours;
        private String weekId;
    }
}
