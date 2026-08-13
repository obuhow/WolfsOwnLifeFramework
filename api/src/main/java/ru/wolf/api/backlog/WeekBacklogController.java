package ru.wolf.api.backlog;

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
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.List;

@RestController
@RequestMapping("/api/v1/backlog")
@RequiredArgsConstructor
public class WeekBacklogController {

    private final WeekBacklogRepository weekBacklogRepository;
    private final DeloRepository deloRepository;
    private final UserRepository userRepository;

    @GetMapping("/week")
    @Transactional(readOnly = true)
    public ResponseEntity<WeekBacklogResponse> getCurrentWeek(Authentication authentication) {
        User user = currentUser(authentication);
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDate today = LocalDate.now(zone);
        WeekFields wf = WeekFields.ISO;
        int isoYear = today.get(wf.weekBasedYear());
        int isoWeek = today.get(wf.weekOfWeekBasedYear());
        return getWeek(authentication, isoYear, isoWeek);
    }

    @GetMapping("/week/{isoYear}/{isoWeek}")
    @Transactional(readOnly = true)
    public ResponseEntity<WeekBacklogResponse> getWeek(
            Authentication authentication,
            @PathVariable Integer isoYear,
            @PathVariable Integer isoWeek
    ) {
        User user = currentUser(authentication);
        WeekBacklog backlog = weekBacklogRepository
                .findByUserAndIsoYearAndIsoWeek(user, isoYear, isoWeek)
                .orElseGet(() -> emptyBacklog(user, isoYear, isoWeek));

        List<DeloResponse> deloResponses = backlog.getDelos().stream()
                .map(this::toDeloResponse)
                .sorted((a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()))
                .toList();

        // Monday of the ISO week
        LocalDate monday = LocalDate.of(isoYear, 1, 4)
                .with(WeekFields.ISO.weekOfWeekBasedYear(), isoWeek)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate nextMonday = monday.plusDays(7);

        return ResponseEntity.ok(new WeekBacklogResponse(
                isoYear,
                isoWeek,
                monday.toString(),
                nextMonday.toString(),
                deloResponses
        ));
    }

    @PostMapping("/week/{isoYear}/{isoWeek}/delos/{deloId}")
    @Transactional
    public ResponseEntity<WeekBacklogResponse> addDeloToWeek(
            Authentication authentication,
            @PathVariable Integer isoYear,
            @PathVariable Integer isoWeek,
            @PathVariable Long deloId
    ) {
        User user = currentUser(authentication);
        Delo delo = deloRepository.findByUserAndId(user, deloId)
                .orElseThrow(() -> new IllegalArgumentException("Дело не найдено"));

        WeekBacklog backlog = weekBacklogRepository
                .findByUserAndIsoYearAndIsoWeek(user, isoYear, isoWeek)
                .orElseGet(() -> emptyBacklog(user, isoYear, isoWeek));

        backlog.addDelo(delo);
        backlog = weekBacklogRepository.save(backlog);

        return getWeek(authentication, isoYear, isoWeek);
    }

    @DeleteMapping("/week/{isoYear}/{isoWeek}/delos/{deloId}")
    @Transactional
    public ResponseEntity<WeekBacklogResponse> removeDeloFromWeek(
            Authentication authentication,
            @PathVariable Integer isoYear,
            @PathVariable Integer isoWeek,
            @PathVariable Long deloId
    ) {
        User user = currentUser(authentication);
        WeekBacklog backlog = weekBacklogRepository
                .findByUserAndIsoYearAndIsoWeek(user, isoYear, isoWeek)
                .orElseThrow(() -> new IllegalArgumentException("Бэклог недели не найден"));

        Delo delo = deloRepository.findByUserAndId(user, deloId)
                .orElseThrow(() -> new IllegalArgumentException("Дело не найдено"));

        backlog.removeDelo(delo);
        backlog = weekBacklogRepository.save(backlog);

        return getWeek(authentication, isoYear, isoWeek);
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<WeekBacklogSummary>> listAllWeeks(Authentication authentication) {
        User user = currentUser(authentication);
        List<WeekBacklog> backlogs = weekBacklogRepository.findByUserOrderByYearDescWeekDesc(user);
        return ResponseEntity.ok(backlogs.stream()
                .map(wb -> new WeekBacklogSummary(
                        wb.getIsoYear(),
                        wb.getIsoWeek(),
                        wb.getDelos().size()
                ))
                .toList());
    }

    private WeekBacklog emptyBacklog(User user, Integer isoYear, Integer isoWeek) {
        WeekBacklog wb = new WeekBacklog();
        wb.setUser(user);
        wb.setIsoYear(isoYear);
        wb.setIsoWeek(isoWeek);
        wb.setDelos(new java.util.HashSet<>());
        return wb;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private DeloResponse toDeloResponse(Delo delo) {
        return new DeloResponse(
                delo.getId(),
                delo.getTitle(),
                delo.getExecutionMode()
        );
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeekBacklogResponse {
        private Integer isoYear;
        private Integer isoWeek;
        private String weekStart;
        private String weekEndExclusive;
        private List<DeloResponse> delos;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeloResponse {
        private Long id;
        private String title;
        private Delo.ExecutionMode executionMode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeekBacklogSummary {
        private Integer isoYear;
        private Integer isoWeek;
        private Integer deloCount;
    }
}