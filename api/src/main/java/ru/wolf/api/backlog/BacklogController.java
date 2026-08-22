package ru.wolf.api.backlog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.List;

@RestController
@RequestMapping("/api/v1/backlog")
@RequiredArgsConstructor
public class BacklogController {
    private final BacklogItemRepository items;
    private final DeloRepository delos;
    private final UserRepository users;

    @GetMapping
    @Transactional(readOnly = true)
    public List<Response> list(Authentication auth, @RequestParam String scope, @RequestParam String period) {
        User user = current(auth);
        BacklogItem.Scope parsedScope = BacklogItem.Scope.valueOf(scope.toUpperCase());
        return items.findPeriod(user, parsedScope, period).stream().map(this::response).toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Response> create(Authentication auth, @Valid @RequestBody Request request) {
        User user = current(auth);
        Delo delo = delos.findByUserAndId(user, request.deloId()).orElseThrow();
        if (items.findByUserAndDeloIdAndScopeAndPeriodId(user, delo.getId(), request.scope(), request.period()).isPresent()) return ResponseEntity.status(409).build();
        BacklogItem saved = items.save(BacklogItem.builder().user(user).delo(delo).scope(request.scope()).periodId(request.period()).plannedHours(request.plannedHours()).position(request.position() == null ? 0 : request.position()).build());
        return ResponseEntity.status(HttpStatus.CREATED).body(response(saved));
    }

    @PatchMapping("/{id}")
    @Transactional
    public ResponseEntity<Response> update(Authentication auth, @PathVariable Long id, @RequestBody Request request) {
        BacklogItem item = owned(auth, id);
        if (request.plannedHours() != null) item.setPlannedHours(request.plannedHours());
        if (request.position() != null) item.setPosition(request.position());
        return ResponseEntity.ok(response(items.save(item)));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable Long id) { items.delete(owned(auth, id)); return ResponseEntity.noContent().build(); }

    @PostMapping("/{id}/move-to-week")
    @Transactional
    public ResponseEntity<Response> moveToWeek(Authentication auth, @PathVariable Long id, @RequestBody MoveRequest request) {
        BacklogItem month = owned(auth, id);
        if (month.getScope() != BacklogItem.Scope.MONTH) throw new IllegalArgumentException("Переносить можно только месячный бэклог");
        User user = current(auth);
        if (items.findByUserAndDeloIdAndScopeAndPeriodId(user, month.getDelo().getId(), BacklogItem.Scope.WEEK, request.week()).isEmpty()) items.save(BacklogItem.builder().user(user).delo(month.getDelo()).scope(BacklogItem.Scope.WEEK).periodId(request.week()).plannedHours(month.getPlannedHours()).position(0).build());
        month.setMovedToWeek(request.week());
        return ResponseEntity.ok(response(items.save(month)));
    }

    private BacklogItem owned(Authentication auth, Long id) { return items.findByUserAndId(current(auth), id).orElseThrow(); }
    private User current(Authentication auth) { return users.findByUsername(auth.getName()).orElseThrow(); }
    private Response response(BacklogItem item) { return new Response(item.getId(), item.getDelo().getId(), item.getDelo().getTitle(), item.getScope().name().toLowerCase(), item.getPeriodId(), item.getPlannedHours(), item.getPosition(), item.getMovedToWeek()); }
    public record ScopeParam(BacklogItem.Scope value) { public ScopeParam { value = value == null ? BacklogItem.Scope.WEEK : value; } }
    public record Request(@NotNull Long deloId, @NotNull BacklogItem.Scope scope, @NotBlank String period, BigDecimal plannedHours, Integer position) {}
    public record MoveRequest(@NotBlank String week) {}
    public record Response(Long id, Long deloId, String deloTitle, String scope, String period, BigDecimal plannedHours, Integer position, String movedToWeek) {}
}
