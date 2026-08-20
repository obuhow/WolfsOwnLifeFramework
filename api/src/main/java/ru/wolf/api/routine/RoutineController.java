package ru.wolf.api.routine;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.goal.Goal;
import ru.wolf.api.goal.GoalRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/routines")
@RequiredArgsConstructor
public class RoutineController {

    private final RoutineRepository routineRepository;
    private final RoutineScheduleRepository scheduleRepository;
    private final RoutineGoalRepository routineGoalRepository;
    private final GoalRepository goalRepository;
    private final UserRepository userRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<RoutineResponse>> list(Authentication authentication,
                                                       @RequestParam(defaultValue = "false") boolean includeArchived) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(routineRepository.findByUserAndArchivedOrderByTitleAsc(user, includeArchived)
                .stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<RoutineResponse> get(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(toResponse(findRoutine(currentUser(authentication), id)));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<RoutineResponse> create(Authentication authentication,
                                                   @Valid @RequestBody RoutineRequest request) {
        User user = currentUser(authentication);
        Routine routine = Routine.builder()
                .user(user)
                .title(request.getTitle().trim())
                .description(normalize(request.getDescription()))
                .weeklyHours(request.getWeeklyHours().setScale(2))
                .color(normalize(request.getColor()))
                .icon(normalize(request.getIcon()))
                .archived(false)
                .build();
        return ResponseEntity.ok(toResponse(routineRepository.save(routine)));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<RoutineResponse> update(Authentication authentication,
                                                   @PathVariable Long id,
                                                   @Valid @RequestBody RoutineRequest request) {
        Routine routine = findRoutine(currentUser(authentication), id);
        routine.setTitle(request.getTitle().trim());
        routine.setDescription(normalize(request.getDescription()));
        routine.setWeeklyHours(request.getWeeklyHours().setScale(2));
        routine.setColor(normalize(request.getColor()));
        routine.setIcon(normalize(request.getIcon()));
        return ResponseEntity.ok(toResponse(routineRepository.save(routine)));
    }

    @PostMapping("/{id}/archive")
    @Transactional
    public ResponseEntity<RoutineResponse> archive(Authentication authentication, @PathVariable Long id) {
        Routine routine = findRoutine(currentUser(authentication), id);
        routine.setArchived(!routine.isArchived());
        return ResponseEntity.ok(toResponse(routineRepository.save(routine)));
    }

    @PostMapping("/{id}/schedules")
    @Transactional
    public ResponseEntity<ScheduleResponse> addSchedule(Authentication authentication,
                                                         @PathVariable Long id,
                                                         @Valid @RequestBody ScheduleRequest request) {
        Routine routine = findRoutine(currentUser(authentication), id);
        LocalTime start = LocalTime.parse(request.getStartTime());
        LocalTime end = LocalTime.parse(request.getEndTime());
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Время окончания должно быть позже времени начала");
        }
        RoutineSchedule saved = scheduleRepository.save(RoutineSchedule.builder()
                .routine(routine)
                .dayOfWeek(DayOfWeek.valueOf(request.getDayOfWeek().toUpperCase()))
                .startTime(start)
                .endTime(end)
                .build());
        return ResponseEntity.ok(toScheduleResponse(saved));
    }

    @DeleteMapping("/{id}/schedules/{scheduleId}")
    @Transactional
    public ResponseEntity<Void> deleteSchedule(Authentication authentication,
                                                @PathVariable Long id,
                                                @PathVariable Long scheduleId) {
        Routine routine = findRoutine(currentUser(authentication), id);
        RoutineSchedule schedule = scheduleRepository.findById(scheduleId)
                .filter(item -> item.getRoutine().getId().equals(routine.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Расписание не найдено"));
        scheduleRepository.delete(schedule);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/goals/{goalId}")
    @Transactional
    public ResponseEntity<GoalLinkResponse> linkGoal(Authentication authentication,
                                                      @PathVariable Long id,
                                                      @PathVariable Long goalId) {
        User user = currentUser(authentication);
        Routine routine = findRoutine(user, id);
        Goal goal = goalRepository.findByUserAndId(user, goalId)
                .orElseThrow(() -> new IllegalArgumentException("Цель не найдена"));
        RoutineGoalId linkId = new RoutineGoalId(routine.getId(), goal.getId());
        if (!routineGoalRepository.existsById(linkId)) {
            routineGoalRepository.save(RoutineGoal.builder().id(linkId).routine(routine).goal(goal).build());
        }
        return ResponseEntity.ok(new GoalLinkResponse(goal.getId(), goal.getTitle()));
    }

    @DeleteMapping("/{id}/goals/{goalId}")
    @Transactional
    public ResponseEntity<Void> unlinkGoal(Authentication authentication,
                                            @PathVariable Long id,
                                            @PathVariable Long goalId) {
        User user = currentUser(authentication);
        Routine routine = findRoutine(user, id);
        goalRepository.findByUserAndId(user, goalId)
                .orElseThrow(() -> new IllegalArgumentException("Цель не найдена"));
        routineGoalRepository.deleteById(new RoutineGoalId(routine.getId(), goalId));
        return ResponseEntity.noContent().build();
    }

    private Routine findRoutine(User user, Long id) {
        return routineRepository.findByUserAndId(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Рутина не найдена"));
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private RoutineResponse toResponse(Routine routine) {
        List<ScheduleResponse> schedules = scheduleRepository.findByRoutineIdOrderByDayOfWeekAscStartTimeAsc(routine.getId())
                .stream().map(this::toScheduleResponse).toList();
        List<GoalLinkResponse> goals = routineGoalRepository.findByRoutineId(routine.getId()).stream()
                .map(link -> new GoalLinkResponse(link.getGoal().getId(), link.getGoal().getTitle())).toList();
        return new RoutineResponse(routine.getId(), routine.getTitle(), routine.getDescription(), routine.getWeeklyHours(),
                routine.getColor(), routine.getIcon(), routine.isArchived(), schedules,
                goals.stream().map(GoalLinkResponse::getGoalId).toList(), goals);
    }

    private ScheduleResponse toScheduleResponse(RoutineSchedule schedule) {
        return new ScheduleResponse(schedule.getId(), schedule.getDayOfWeek().name(),
                schedule.getStartTime().toString(), schedule.getEndTime().toString());
    }

    private String normalize(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoutineRequest {
        @NotBlank @Size(max = 200) private String title;
        @Size(max = 10000) private String description;
        @NotNull @DecimalMin(value = "0.0", inclusive = true) private BigDecimal weeklyHours;
        @Size(max = 7) private String color;
        @Size(max = 50) private String icon;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleRequest {
        @NotNull private String dayOfWeek;
        @NotNull private String startTime;
        @NotNull private String endTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoutineResponse {
        private Long id;
        private String title;
        private String description;
        private BigDecimal weeklyHours;
        private String color;
        private String icon;
        private boolean archived;
        private List<ScheduleResponse> schedules;
        private List<Long> goalIds;
        private List<GoalLinkResponse> goals;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleResponse {
        private Long id;
        private String dayOfWeek;
        private String startTime;
        private String endTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GoalLinkResponse {
        private Long goalId;
        private String goalTitle;
    }
}

// end
