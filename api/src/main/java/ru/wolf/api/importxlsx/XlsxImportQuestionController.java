package ru.wolf.api.importxlsx;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.user.UserRepository;
import java.util.List;

@RestController
@RequestMapping("/api/v1/import/xlsx")
@RequiredArgsConstructor
public class XlsxImportQuestionController {
    private final UserRepository users;
    private final XlsxImportRunRepository runs;
    private final XlsxImportQuestionRepository questions;

    @GetMapping("/{id}/questions")
    public ResponseEntity<List<XlsxImportQuestionResponse>> questions(Authentication auth, @PathVariable Long id) {
        var user = users.findByUsername(auth.getName()).orElseThrow();
        runs.findById(id).filter(run -> run.getUser().getId().equals(user.getId())).orElseThrow();
        return ResponseEntity.ok(questions.findByImportRunIdAndResolvedFalseOrderByStartAtAsc(id).stream()
                .map(q -> new XlsxImportQuestionResponse(q.getId(), q.getActivityText(), q.getSheetName(), q.getStartAt()))
                .toList());
    }

    public record XlsxImportQuestionResponse(Long id, String activityText, String sheetName, java.time.LocalDateTime startAt) {}
}
