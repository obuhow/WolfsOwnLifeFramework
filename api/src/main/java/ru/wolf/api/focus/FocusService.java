package ru.wolf.api.focus;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import ru.wolf.api.focus.dto.*;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.time.LocalDateTime;
import java.util.Map;

@Service @RequiredArgsConstructor
public class FocusService {
    private final UserRepository users;
    private final DeloRepository delos;
    private final FocusSessionRepository sessions;
    private final FocusDistractionRepository distractions;
    private final TimeEntryRepository entries;

    public record Result(int status, Object body) {}
    private Result ok(Object body) { return new Result(200, body); }
    private Result conflict(String code) { return new Result(409, Map.of("code", code)); }

    @Transactional
    public Result start(String username, StartRequest request) {
        User user = current(username);
        if (!"PRIMARY_FOCUS".equals(user.getTimeCaptureMode())) return conflict("TIME_CAPTURE_MODE_MISMATCH");
        if (sessions.findFirstByUserAndEndedAtIsNullOrderByStartedAtDesc(user).isPresent()) return conflict("FOCUS_ALREADY_OPEN");
        Delo delo = delos.findByUserAndId(user, request.deloId()).orElseThrow();
        FocusSession session = sessions.save(FocusSession.builder().user(user).delo(delo)
                .startedAt(request.startedAt() == null ? LocalDateTime.now() : request.startedAt()).build());
        return ok(toResponse(session));
    }

    @Transactional(readOnly = true)
    public Result currentSession(String username) {
        return ok(sessions.findFirstByUserAndEndedAtIsNullOrderByStartedAtDesc(current(username))
                .map(this::toResponse).orElse(null));
    }

    @Transactional(readOnly = true)
    public Result details(String username, Long id) {
        User user = current(username);
        FocusSession session = sessions.findById(id).filter(item -> item.getUser().getId().equals(user.getId())).orElseThrow();
        var items = distractions.findBySessionIdOrderByAtAsc(id).stream()
                .map(item -> Map.of("id", item.getId(), "deloId", item.getDelo() == null ? 0L : item.getDelo().getId(),
                        "text", item.getText() == null ? "" : item.getText(), "at", item.getAt(),
                        "minutes", item.getMinutes() == null ? 0 : item.getMinutes())).toList();
        return ok(Map.of("id", session.getId(), "deloId", session.getDelo().getId(), "startedAt", session.getStartedAt(),
                "endedAt", session.getEndedAt() == null ? "" : session.getEndedAt(), "distractions", items));
    }

    @Transactional
    public void deleteDistraction(String username, Long sessionId, Long distractionId) {
        User user = current(username);
        sessions.findById(sessionId).filter(item -> item.getUser().getId().equals(user.getId())).orElseThrow();
        distractions.findById(distractionId).filter(item -> item.getSession().getId().equals(sessionId)).ifPresent(distractions::delete);
    }

    @Transactional
    public Result stop(String username, Long id, StopRequest request) {
        User user = current(username);
        FocusSession session = sessions.findById(id).filter(item -> item.getUser().getId().equals(user.getId())).orElseThrow();
        if (session.getEndedAt() == null) {
            LocalDateTime endedAt = request != null && request.endedAt() != null ? request.endedAt() : LocalDateTime.now();
            session.setEndedAt(endedAt);
            Materialization materialization = materialize(user, session);
            sessions.save(session);
            return ok(Map.of("session", toResponse(session), "materialized", materialization.materialized(),
                    "skippedCells", materialization.skippedCells(), "sessionMinutes",
                    java.time.Duration.between(session.getStartedAt(), endedAt).toMinutes()));
        }
        return ok(toResponse(session));
    }

    @Transactional
    public Result distraction(String username, Long id, DistractionRequest request) {
        User user = current(username);
        FocusSession session = sessions.findById(id).filter(item -> item.getUser().getId().equals(user.getId())).orElseThrow();
        if (request.deloId() == null && (request.text() == null || request.text().isBlank()))
            throw new IllegalArgumentException("Укажите Дело или текст");
        Delo delo = request.deloId() == null ? null : delos.findByUserAndId(user, request.deloId()).orElseThrow();
        FocusDistraction saved = distractions.save(FocusDistraction.builder().session(session).delo(delo).text(request.text())
                .at(request.at() == null ? LocalDateTime.now() : request.at()).minutes(request.minutes()).build());
        return ok(Map.of("id", saved.getId()));
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
    private User current(String username) { return users.findByUsername(username).orElseThrow(); }
    private Map<String, Object> toResponse(FocusSession session) { return Map.of("id", session.getId(), "deloId", session.getDelo().getId(), "startedAt", session.getStartedAt(), "endedAt", session.getEndedAt() == null ? "" : session.getEndedAt()); }
    private record Materialization(java.util.List<java.util.Map<String, String>> materialized, java.util.List<String> skippedCells) {}
}
