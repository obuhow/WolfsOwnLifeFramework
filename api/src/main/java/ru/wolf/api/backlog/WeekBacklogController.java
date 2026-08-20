package ru.wolf.api.backlog;

import lombok.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.time.*;
import java.time.temporal.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/backlog")
@RequiredArgsConstructor
public class WeekBacklogController {
    private final BacklogItemRepository items;
    private final DeloRepository delos;
    private final UserRepository users;

    @GetMapping("/week")
    public ResponseEntity<WeekBacklogResponse> current(Authentication auth) {
        LocalDate today = LocalDate.now(ZoneId.of(currentUser(auth).getTimezone()));
        return weekResponse(auth, today.get(WeekFields.ISO.weekBasedYear()), today.get(WeekFields.ISO.weekOfWeekBasedYear()));
    }

    @GetMapping("/week/{year}/{week}")
    @Transactional(readOnly = true)
    public ResponseEntity<WeekBacklogResponse> weekResponse(Authentication auth, @PathVariable int year, @PathVariable int week) {
        User user = currentUser(auth); String period = "%d-W%02d".formatted(year, week);
        List<DeloResponse> result = items.findPeriod(user, BacklogItem.Scope.WEEK, period).stream().map(this::delo).toList();
        LocalDate monday = LocalDate.of(year, 1, 4).with(WeekFields.ISO.weekOfWeekBasedYear(), week).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return ResponseEntity.ok(new WeekBacklogResponse(year, week, monday.toString(), monday.plusDays(7).toString(), result));
    }

    @PostMapping("/week/{year}/{week}/delos/{deloId}")
    @Transactional
    public ResponseEntity<WeekBacklogResponse> add(Authentication auth, @PathVariable int year, @PathVariable int week, @PathVariable Long deloId) {
        User user = currentUser(auth); Delo delo = delos.findByUserAndId(user, deloId).orElseThrow(); String period = "%d-W%02d".formatted(year, week);
        if (items.findByUserAndDeloIdAndScopeAndPeriodId(user, deloId, BacklogItem.Scope.WEEK, period).isEmpty()) items.save(BacklogItem.builder().user(user).delo(delo).scope(BacklogItem.Scope.WEEK).periodId(period).position(0).build());
        return weekResponse(auth, year, week);
    }

    @DeleteMapping("/week/{year}/{week}/delos/{deloId}")
    @Transactional
    public ResponseEntity<WeekBacklogResponse> remove(Authentication auth, @PathVariable int year, @PathVariable int week, @PathVariable Long deloId) {
        BacklogItem item = items.findByUserAndDeloIdAndScopeAndPeriodId(currentUser(auth), deloId, BacklogItem.Scope.WEEK, "%d-W%02d".formatted(year, week)).orElseThrow(); items.delete(item); return weekResponse(auth, year, week);
    }

    private User currentUser(Authentication auth) { return users.findByUsername(auth.getName()).orElseThrow(); }
    private DeloResponse delo(BacklogItem item) { return new DeloResponse(item.getDelo().getId(), item.getDelo().getTitle(), item.getDelo().getExecutionMode(), item.getPlannedHours()); }
    @Data @NoArgsConstructor @AllArgsConstructor public static class WeekBacklogResponse { private Integer isoYear; private Integer isoWeek; private String weekStart; private String weekEndExclusive; private List<DeloResponse> delos; }
    @Data @NoArgsConstructor @AllArgsConstructor public static class DeloResponse { private Long id; private String title; private Delo.ExecutionMode executionMode; private java.math.BigDecimal plannedHours; }
}