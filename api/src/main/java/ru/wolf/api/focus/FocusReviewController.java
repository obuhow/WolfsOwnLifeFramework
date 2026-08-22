package ru.wolf.api.focus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/focus")
@RequiredArgsConstructor
public class FocusReviewController {
    private final UserRepository users;
    private final DeloRepository delos;
    private final FocusSessionRepository sessions;
    private final FocusDistractionRepository distractions;
    private final TimeEntryRepository entries;

    @GetMapping("/{id}/review")
    @Transactional(readOnly = true)
    public ReviewResponse review(Authentication auth, @PathVariable Long id) {
        FocusSession session = ownedSession(auth, id);
        List<DistractionResponse> items = distractions.findBySessionIdOrderByAtAsc(id).stream()
                .map(d -> new DistractionResponse(d.getId(), d.getAt(), d.getDelo() == null ? null : d.getDelo().getId(), d.getDelo() == null ? null : d.getDelo().getTitle(), d.getText(), suggestedMinutes(d), d.getAppliedAt() != null, d.getAppliedMinutes()))
                .toList();
        return new ReviewResponse(id, session.getDelo().getId(), session.getDelo().getTitle(), session.getStartedAt(), session.getEndedAt(), items);
    }

    @PostMapping("/{id}/review/preview")
    @Transactional(readOnly = true)
    public PreviewResponse preview(Authentication auth, @PathVariable Long id, @Valid @RequestBody AllocationRequest request) {
        FocusSession session = ownedSession(auth, id);
        Plan plan = plan(session, auth, request.allocations(), false);
        return plan.preview();
    }

    @PostMapping("/{id}/review/apply")
    @Transactional
    public PreviewResponse apply(Authentication auth, @PathVariable Long id, @Valid @RequestBody AllocationRequest request) {
        FocusSession session = ownedSession(auth, id);
        Plan plan = plan(session, auth, request.allocations(), true);
        if (!plan.occupied().isEmpty()) throw new IllegalArgumentException("Часть ячеек занята другим Дело; сначала проверьте предпросмотр");
        Map<LocalDateTime, Delo> targetBySlot = plan.changes().stream().collect(java.util.stream.Collectors.toMap(Change::slot, c -> delo(auth, c.targetDeloId())));
        rewriteMainEntries(session, targetBySlot);
        LocalDateTime appliedAt = LocalDateTime.now();
        for (Allocation allocation : request.allocations()) {
            FocusDistraction distraction = distraction(session, allocation.distractionId());
            if (allocation.minutes() != null && allocation.minutes() > 0) {
                if (allocation.deloId() != null) distraction.setDelo(delo(auth, allocation.deloId()));
                distraction.setAppliedMinutes(allocation.minutes());
                distraction.setAppliedAt(appliedAt);
                distractions.save(distraction);
            }
        }
        return plan.preview();
    }

    @PostMapping("/{id}/review/revert")
    @Transactional
    public PreviewResponse revert(Authentication auth, @PathVariable Long id) {
        FocusSession session = ownedSession(auth, id);
        List<FocusDistraction> applied = distractions.findBySessionIdOrderByAtAsc(id).stream().filter(d -> d.getAppliedAt() != null && d.getAppliedMinutes() != null).toList();
        List<Change> changes = new ArrayList<>();
        for (FocusDistraction d : applied) {
            Delo target = d.getDelo();
            for (LocalDateTime slot : slotsFor(d, d.getAppliedMinutes(), session)) {
                TimeEntry entry = entries.findCoveringSlot(session.getUser().getId(), slot).orElse(null);
                if (entry != null && entry.getDelo() != null && entry.getDelo().getId().equals(target.getId())) changes.add(new Change(slot, target.getId(), target.getTitle(), session.getDelo().getId(), session.getDelo().getTitle()));
            }
        }
        Map<LocalDateTime, Delo> restore = changes.stream().collect(java.util.stream.Collectors.toMap(Change::slot, c -> session.getDelo()));
        rewriteTargetEntries(session, applied, restore);
        for (FocusDistraction d : applied) { d.setAppliedAt(null); d.setAppliedMinutes(null); distractions.save(d); }
        return new PreviewResponse(changes, List.of());
    }

    private Plan plan(FocusSession session, Authentication auth, List<Allocation> allocations, boolean apply) {
        if (session.getEndedAt() == null) throw new IllegalArgumentException("Разбирать можно только завершённую сессию");
        int total = allocations.stream().mapToInt(a -> a.minutes() == null ? 0 : a.minutes()).sum();
        long duration = Duration.between(session.getStartedAt(), session.getEndedAt()).toMinutes();
        if (total > duration) throw new IllegalArgumentException("Сумма разбора больше длительности сессии; уменьшите минуты");
        List<Change> changes = new ArrayList<>();
        List<Occupied> occupied = new ArrayList<>();
        for (Allocation allocation : allocations) {
            if (allocation.minutes() == null) continue;
            if (allocation.minutes() <= 0 || allocation.minutes() % 15 != 0) throw new IllegalArgumentException("Минуты разбора должны быть положительными и кратными 15");
            FocusDistraction distraction = distraction(session, allocation.distractionId());
            if (distraction.getAppliedAt() != null && Objects.equals(distraction.getAppliedMinutes(), allocation.minutes())) continue;
            Delo target = delo(auth, allocation.deloId() == null ? (distraction.getDelo() == null ? null : distraction.getDelo().getId()) : allocation.deloId());
            if (target == null) throw new IllegalArgumentException("Укажите Дело для разбора отметки");
            for (LocalDateTime slot : slotsFor(distraction, allocation.minutes(), session)) {
                TimeEntry entry = entries.findCoveringSlot(session.getUser().getId(), slot).orElse(null);
                if (entry == null || entry.getDelo() == null) continue;
                if (!entry.getDelo().getId().equals(session.getDelo().getId())) occupied.add(new Occupied(slot, entry.getDelo().getTitle()));
                else changes.add(new Change(slot, session.getDelo().getId(), session.getDelo().getTitle(), target.getId(), target.getTitle()));
            }
        }
        return new Plan(new PreviewResponse(changes, occupied), occupied);
    }

    private void rewriteMainEntries(FocusSession session, Map<LocalDateTime, Delo> targetBySlot) {
        List<TimeEntry> main = entries.findOverlapping(session.getUser().getId(), session.getStartedAt(), session.getEndedAt()).stream().filter(e -> e.getDelo() != null && e.getDelo().getId().equals(session.getDelo().getId())).toList();
        for (TimeEntry entry : main) { entries.delete(entry); rewrite(entry, targetBySlot, session.getDelo()); }
    }

    private void rewriteTargetEntries(FocusSession session, List<FocusDistraction> applied, Map<LocalDateTime, Delo> restore) {
        Set<Long> targetIds = applied.stream().map(d -> d.getDelo().getId()).collect(java.util.stream.Collectors.toSet());
        List<TimeEntry> targetEntries = entries.findOverlapping(session.getUser().getId(), session.getStartedAt(), session.getEndedAt()).stream().filter(e -> e.getDelo() != null && targetIds.contains(e.getDelo().getId())).toList();
        for (TimeEntry entry : targetEntries) { entries.delete(entry); rewrite(entry, restore, entry.getDelo()); }
    }

    private void rewrite(TimeEntry entry, Map<LocalDateTime, Delo> targetBySlot, Delo defaultDelo) {
        LocalDateTime cursor = entry.getStartAt();
        while (cursor.isBefore(entry.getEndAt())) {
            Delo target = targetBySlot.getOrDefault(cursor, defaultDelo);
            LocalDateTime end = cursor.plusMinutes(15);
            while (end.isBefore(entry.getEndAt()) && target.getId().equals(targetBySlot.getOrDefault(end, defaultDelo).getId())) end = end.plusMinutes(15);
            entries.save(TimeEntry.builder().user(entry.getUser()).delo(target).startAt(cursor).endAt(end).status(entry.getStatus()).build());
            cursor = end;
        }
    }

    private List<LocalDateTime> slotsFor(FocusDistraction distraction, int minutes, FocusSession session) {
        LocalDateTime start = floor15(distraction.getAt());
        LocalDateTime sessionStart = floor15(session.getStartedAt());
        LocalDateTime sessionEnd = ceil15(session.getEndedAt());
        if (start.isBefore(sessionStart)) start = sessionStart;
        if (start.plusMinutes(minutes).isAfter(sessionEnd)) throw new IllegalArgumentException("Разбор выходит за границы сессии");
        List<LocalDateTime> result = new ArrayList<>();
        for (LocalDateTime cursor = start; cursor.isBefore(start.plusMinutes(minutes)); cursor = cursor.plusMinutes(15)) result.add(cursor);
        return result;
    }

    private Integer suggestedMinutes(FocusDistraction d) { return d.getMinutes() == null ? null : (int) (Math.ceil(d.getMinutes() / 15.0) * 15); }
    private FocusSession ownedSession(Authentication auth, Long id) { 
        User user = current(auth); 
        return sessions.findByUserAndId(user, id).orElseThrow(); 
    }
    private FocusDistraction distraction(FocusSession session, Long id) { 
        return distractions.findBySessionIdAndId(session.getId(), id).orElseThrow(); 
    }
    private Delo delo(Authentication auth, Long id) { return id == null ? null : delos.findByUserAndId(current(auth), id).orElseThrow(); }
    private User current(Authentication auth) { return users.findByUsername(auth.getName()).orElseThrow(); }
    private LocalDateTime floor15(LocalDateTime value) { return value.withSecond(0).withNano(0).minusMinutes(value.getMinute() % 15); }
    private LocalDateTime ceil15(LocalDateTime value) { LocalDateTime floor = floor15(value); return floor.equals(value.withSecond(0).withNano(0)) ? floor : floor.plusMinutes(15); }

    public record AllocationRequest(@NotNull List<Allocation> allocations) {}
    public record Allocation(Long distractionId, Long deloId, Integer minutes) {}
    public record ReviewResponse(Long sessionId, Long mainDeloId, String mainDeloTitle, LocalDateTime startedAt, LocalDateTime endedAt, List<DistractionResponse> distractions) {}
    public record DistractionResponse(Long id, LocalDateTime at, Long deloId, String deloTitle, String text, Integer suggestedMinutes, boolean applied, Integer appliedMinutes) {}
    public record PreviewResponse(List<Change> changes, List<Occupied> occupied) {}
    public record Change(LocalDateTime slot, Long sourceDeloId, String sourceDeloTitle, Long targetDeloId, String targetDeloTitle) {}
    public record Occupied(LocalDateTime slot, String deloTitle) {}
    private record Plan(PreviewResponse preview, List<Occupied> occupied) { List<Change> changes() { return preview.changes(); } }
}
