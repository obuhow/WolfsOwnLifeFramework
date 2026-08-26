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
package ru.wolf.api.focus;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/focus")
@RequiredArgsConstructor
public class FocusController {
    private final UserRepository users;
    private final DeloRepository delos;
    private final FocusSessionRepository sessions;
    private final FocusDistractionRepository distractions;
    private final TimeEntryRepository entries;

    @PostMapping("/start")
    @Transactional
    public ResponseEntity<?> start(Authentication auth, @RequestBody StartRequest request) {
        User user = current(auth);
        if (!"PRIMARY_FOCUS".equals(user.getTimeCaptureMode())) return ResponseEntity.status(409).body(Map.of("code", "TIME_CAPTURE_MODE_MISMATCH"));
        if (sessions.findFirstByUserAndEndedAtIsNullOrderByStartedAtDesc(user).isPresent()) return ResponseEntity.status(409).body(Map.of("code", "FOCUS_ALREADY_OPEN"));
        Delo delo = delos.findByUserAndId(user, request.deloId()).orElseThrow();
        FocusSession session = sessions.save(FocusSession.builder().user(user).delo(delo).startedAt(request.startedAt() == null ? LocalDateTime.now() : request.startedAt()).build());
        return ResponseEntity.ok(toResponse(session));
    }

    @GetMapping("/current")
    @Transactional(readOnly = true)
    public ResponseEntity<?> currentSession(Authentication auth) {
        return ResponseEntity.ok(sessions.findFirstByUserAndEndedAtIsNullOrderByStartedAtDesc(current(auth)).map(this::toResponse).orElse(null));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> details(Authentication auth, @PathVariable Long id) {
        User user = current(auth);
        FocusSession session = sessions.findById(id).filter(item -> item.getUser().getId().equals(user.getId())).orElseThrow();
        var items = distractions.findBySessionIdOrderByAtAsc(id).stream()
                .map(item -> Map.of("id", item.getId(), "deloId", item.getDelo() == null ? 0L : item.getDelo().getId(), "text", item.getText() == null ? "" : item.getText(), "at", item.getAt(), "minutes", item.getMinutes() == null ? 0 : item.getMinutes()))
                .toList();
        return ResponseEntity.ok(Map.of("id", session.getId(), "deloId", session.getDelo().getId(), "startedAt", session.getStartedAt(), "endedAt", session.getEndedAt() == null ? "" : session.getEndedAt(), "distractions", items));
    }

    @DeleteMapping("/{sessionId}/distractions/{distractionId}")
    @Transactional
    public ResponseEntity<Void> deleteDistraction(Authentication auth, @PathVariable Long sessionId, @PathVariable Long distractionId) {
        User user = current(auth);
        sessions.findById(sessionId).filter(item -> item.getUser().getId().equals(user.getId())).orElseThrow();
        distractions.findById(distractionId).filter(item -> item.getSession().getId().equals(sessionId)).ifPresent(distractions::delete);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/stop")
    @Transactional
    public ResponseEntity<?> stop(Authentication auth, @PathVariable Long id, @RequestBody(required = false) StopRequest request) {
        User user = current(auth);
        FocusSession session = sessions.findById(id).filter(item -> item.getUser().getId().equals(user.getId())).orElseThrow();
        if (session.getEndedAt() == null) {
            LocalDateTime endedAt = request != null && request.endedAt() != null ? request.endedAt() : LocalDateTime.now();
            session.setEndedAt(endedAt);
            Materialization materialization = materialize(user, session);
            sessions.save(session);
            return ResponseEntity.ok(Map.of("session", toResponse(session), "materialized", materialization.materialized(), "skippedCells", materialization.skippedCells(), "sessionMinutes", java.time.Duration.between(session.getStartedAt(), endedAt).toMinutes()));
        }
        return ResponseEntity.ok(toResponse(session));
    }

    @PostMapping("/{id}/distractions")
    @Transactional
    public ResponseEntity<?> distraction(Authentication auth, @PathVariable Long id, @RequestBody DistractionRequest request) {
        User user = current(auth);
        FocusSession session = sessions.findById(id).filter(item -> item.getUser().getId().equals(user.getId())).orElseThrow();
        if (request.deloId() == null && (request.text() == null || request.text().isBlank())) throw new IllegalArgumentException("Укажите Дело или текст");
        Delo delo = request.deloId() == null ? null : delos.findByUserAndId(user, request.deloId()).orElseThrow();
        FocusDistraction saved = distractions.save(FocusDistraction.builder().session(session).delo(delo).text(request.text()).at(request.at() == null ? LocalDateTime.now() : request.at()).minutes(request.minutes()).build());
        return ResponseEntity.ok(Map.of("id", saved.getId()));
    }

    private Materialization materialize(User user, FocusSession session) {
        long minutes = java.time.Duration.between(session.getStartedAt(), session.getEndedAt()).toMinutes();
        if (minutes < 15) return new Materialization(java.util.List.of(), java.util.List.of());
        LocalDateTime start = floor15(session.getStartedAt()), end = ceil15(session.getEndedAt());
        java.util.List<String> skipped = new java.util.ArrayList<>();
        java.util.List<java.util.Map<String, String>> saved = new java.util.ArrayList<>();
        LocalDateTime segmentStart = null;
        for (LocalDateTime cursor = start; cursor.isBefore(end); cursor = cursor.plusMinutes(15)) {
            if (entries.findCoveringSlot(user.getId(), cursor).isPresent()) {
                if (segmentStart != null) { saved.add(saveSegment(user, session, segmentStart, cursor)); segmentStart = null; }
                skipped.add(cursor.toString());
            } else if (segmentStart == null) segmentStart = cursor;
        }
        if (segmentStart != null) saved.add(saveSegment(user, session, segmentStart, end));
        return new Materialization(saved, skipped);
    }

    private java.util.Map<String, String> saveSegment(User user, FocusSession session, LocalDateTime start, LocalDateTime end) {
        entries.save(TimeEntry.builder().user(user).delo(session.getDelo()).startAt(start).endAt(end).status(TimeEntry.Status.DONE).build());
        return Map.of("startAt", start.toString(), "endAt", end.toString());
    }
    private LocalDateTime floor15(LocalDateTime value) { return value.withSecond(0).withNano(0).minusMinutes(value.getMinute() % 15); }
    private LocalDateTime ceil15(LocalDateTime value) { LocalDateTime result = floor15(value); return result.equals(value.withSecond(0).withNano(0)) ? result : result.plusMinutes(15); }
    private User current(Authentication auth) { return users.findByUsername(auth.getName()).orElseThrow(); }
    private Map<String, Object> toResponse(FocusSession session) { return Map.of("id", session.getId(), "deloId", session.getDelo().getId(), "startedAt", session.getStartedAt(), "endedAt", session.getEndedAt() == null ? "" : session.getEndedAt()); }
    private record Materialization(java.util.List<java.util.Map<String, String>> materialized, java.util.List<String> skippedCells) {}
    public record StartRequest(Long deloId, LocalDateTime startedAt) {}
    public record StopRequest(LocalDateTime endedAt) {}
    public record DistractionRequest(Long deloId, String text, LocalDateTime at, Integer minutes) {}
}
