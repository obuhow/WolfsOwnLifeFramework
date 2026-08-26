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
package ru.wolf.api.goal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalRepository goalRepository;
    private final GoalMetricRepository metricRepository;
    private final GoalWeekBudgetRepository budgetRepository;
    private final GoalProjectRepository goalProjectRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final GoalFactService goalFactService;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<GoalResponse>> list(
            Authentication authentication,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(goalRepository.findByUserAndArchivedOrderByPriorityAsc(user, includeArchived)
                .stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<GoalDetailResponse> get(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam(required = false) String week) {
        User user = currentUser(authentication);
        Goal goal = findGoal(user, id);
        String requestedWeek = week == null || week.isBlank() ? currentIsoWeek() : week;
        GoalFactService.IsoWeek parsed = goalFactService.parseWeek(requestedWeek);
        return ResponseEntity.ok(toDetail(goal, requestedWeek, parsed));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<GoalResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateGoalRequest request) {
        User user = currentUser(authentication);
        int priority = request.getPriority() == null ? goalRepository.findMaxActivePriority(user) + 1 : request.getPriority();
        if (priority < 1) {
            throw new IllegalArgumentException("Приоритет должен быть положительным");
        }
        if (request.getPriority() != null && goalRepository.existsByUserAndPriority(user, priority)) {
            throw new DuplicateGoalPriorityException(priority);
        }
        Goal goal = Goal.builder()
                .user(user)
                .title(request.getTitle().trim())
                .description(normalize(request.getDescription()))
                .priority(priority)
                .archived(false)
                .build();
        return ResponseEntity.ok(toResponse(goalRepository.save(goal)));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<GoalResponse> update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateGoalRequest request) {
        User user = currentUser(authentication);
        Goal goal = findGoal(user, id);
        if (request.getPriority() != null && !request.getPriority().equals(goal.getPriority())) {
            reorder(user, id, request.getPriority());
            goal = findGoal(user, id);
        }
        goal.setTitle(request.getTitle().trim());
        goal.setDescription(normalize(request.getDescription()));
        return ResponseEntity.ok(toResponse(goalRepository.save(goal)));
    }

    @PostMapping("/{id}/archive")
    @Transactional
    public ResponseEntity<Void> archive(Authentication authentication, @PathVariable Long id) {
        User user = currentUser(authentication);
        Goal goal = findGoal(user, id);
        goal.setArchived(true);
        goalRepository.save(goal);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/priority")
    @Transactional
    public ResponseEntity<List<GoalResponse>> changePriority(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody PriorityRequest request) {
        User user = currentUser(authentication);
        findGoal(user, id);
        reorder(user, id, request.getPriority());
        return ResponseEntity.ok(goalRepository.findByUserAndArchivedOrderByPriorityAsc(user, false)
                .stream().map(this::toResponse).toList());
    }

    @PostMapping("/{id}/budget")
    @Transactional
    public ResponseEntity<BudgetResponse> saveBudget(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody BudgetRequest request) {
        User user = currentUser(authentication);
        Goal goal = findGoal(user, id);
        GoalFactService.IsoWeek week = goalFactService.parseWeek(request.getWeek());
        GoalWeekBudget budget = budgetRepository.findByGoalIdAndIsoYearAndIsoWeek(id, week.year(), week.week())
                .orElseGet(() -> GoalWeekBudget.builder().goal(goal).isoYear(week.year()).isoWeek(week.week()).build());
        budget.setHours(request.getHours().setScale(2, RoundingMode.HALF_UP));
        return ResponseEntity.ok(toBudgetResponse(budgetRepository.save(budget)));
    }

    @GetMapping("/{id}/budgets")
    @Transactional(readOnly = true)
    public ResponseEntity<List<BudgetResponse>> budgets(Authentication authentication, @PathVariable Long id) {
        User user = currentUser(authentication);
        findGoal(user, id);
        return ResponseEntity.ok(budgetRepository.findByGoalIdOrderByIsoYearDescIsoWeekDesc(id)
                .stream().map(this::toBudgetResponse).toList());
    }

    @PostMapping("/{id}/metrics")
    @Transactional
    public ResponseEntity<MetricResponse> addMetric(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody MetricRequest request) {
        User user = currentUser(authentication);
        Goal goal = findGoal(user, id);
        GoalMetric metric = GoalMetric.builder()
                .goal(goal)
                .kind(request.getKind().trim())
                .value(request.getValue())
                .targetValue(request.getTargetValue())
                .at(request.getAt() == null ? LocalDateTime.now() : request.getAt())
                .build();
        return ResponseEntity.ok(toMetricResponse(metricRepository.save(metric)));
    }

    @GetMapping("/{id}/metrics")
    @Transactional(readOnly = true)
    public ResponseEntity<List<MetricResponse>> metrics(Authentication authentication, @PathVariable Long id) {
        User user = currentUser(authentication);
        findGoal(user, id);
        return ResponseEntity.ok(metricRepository.findByGoalIdOrderByAtDesc(id).stream()
                .map(this::toMetricResponse).toList());
    }

    @PatchMapping("/{id}/metrics/{metricId}/increment")
    @Transactional
    public ResponseEntity<MetricResponse> incrementMetric(Authentication authentication, @PathVariable Long id, @PathVariable Long metricId, @Valid @RequestBody IncrementRequest request) {
        Goal goal = findGoal(currentUser(authentication), id);
        GoalMetric metric = metricRepository.findById(metricId).filter(item -> item.getGoal().getId().equals(goal.getId())).orElseThrow(() -> new IllegalArgumentException("Метрика не найдена"));
        BigDecimal next = metric.getValue().add(request.getAmount());
        if (next.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("Значение метрики не может быть ниже нуля");
        if (metric.getTargetValue() != null && next.compareTo(metric.getTargetValue()) > 0) throw new IllegalArgumentException("Значение метрики не может превышать цель");
        metric.setValue(next);
        metric.setAt(LocalDateTime.now());
        return ResponseEntity.ok(toMetricResponse(metricRepository.saveAndFlush(metric)));
    }

    @GetMapping("/{id}/projects")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ProjectResponse>> projects(Authentication authentication, @PathVariable Long id) {
        User user = currentUser(authentication);
        findGoal(user, id);
        return ResponseEntity.ok(goalProjectRepository.findByGoalId(id).stream()
                .map(link -> new ProjectResponse(link.getProject().getId(), link.getProject().getTitle()))
                .toList());
    }

    @PostMapping("/{id}/projects/{projectId}")
    @Transactional
    public ResponseEntity<Void> linkProject(Authentication authentication, @PathVariable Long id, @PathVariable Long projectId) {
        User user = currentUser(authentication);
        Goal goal = findGoal(user, id);
        Project project = projectRepository.findByUserAndId(user, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Проект не найден"));
        if (!goalProjectRepository.existsByGoalIdAndProjectId(id, projectId)) {
            goalProjectRepository.save(GoalProject.builder()
                    .id(new GoalProjectId(id, projectId)).goal(goal).project(project).build());
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/projects/{projectId}")
    @Transactional
    public ResponseEntity<Void> unlinkProject(Authentication authentication, @PathVariable Long id, @PathVariable Long projectId) {
        User user = currentUser(authentication);
        findGoal(user, id);
        goalProjectRepository.deleteById(new GoalProjectId(id, projectId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/fact")
    @Transactional(readOnly = true)
    public ResponseEntity<FactResponse> fact(Authentication authentication, @PathVariable Long id, @RequestParam String week) {
        User user = currentUser(authentication);
        Goal goal = findGoal(user, id);
        GoalFactService.IsoWeek parsed = goalFactService.parseWeek(week);
        return ResponseEntity.ok(new FactResponse(week, goalFactService.calculate(user, goal, week)));
    }

    private void reorder(User user, Long goalId, int requestedPriority) {
        if (requestedPriority < 1) {
            throw new IllegalArgumentException("Приоритет должен быть положительным");
        }
        List<Goal> goals = new ArrayList<>(goalRepository.findByUserAndArchivedOrderByPriorityAsc(user, false));
        Goal selected = goals.stream().filter(g -> g.getId().equals(goalId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Цель не найдена"));
        goals.remove(selected);
        int target = Math.min(requestedPriority, goals.size() + 1) - 1;
        goals.add(target, selected);
        for (int i = 0; i < goals.size(); i++) {
            goals.get(i).setPriority(-(i + 1));
        }
        goalRepository.saveAllAndFlush(goals);
        for (int i = 0; i < goals.size(); i++) {
            goals.get(i).setPriority(i + 1);
        }
        goalRepository.saveAll(goals);
    }

    private Goal findGoal(User user, Long id) {
        return goalRepository.findByUserAndId(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Цель не найдена"));
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private GoalResponse toResponse(Goal goal) {
        return new GoalResponse(goal.getId(), goal.getTitle(), goal.getDescription(), goal.getPriority(), goal.getArchived());
    }

    private GoalDetailResponse toDetail(Goal goal, String week, GoalFactService.IsoWeek parsed) {
        GoalWeekBudget budget = budgetRepository.findByGoalIdAndIsoYearAndIsoWeek(goal.getId(), parsed.year(), parsed.week()).orElse(null);
        return new GoalDetailResponse(toResponse(goal),
                goalProjectRepository.findByGoalId(goal.getId()).stream().map(link -> new ProjectResponse(link.getProject().getId(), link.getProject().getTitle())).toList(),
                metricRepository.findByGoalIdOrderByAtDesc(goal.getId()).stream().map(this::toMetricResponse).toList(),
                budget == null ? null : toBudgetResponse(budget),
                new FactResponse(week, goalFactService.calculate(goal.getUser(), goal, week)));
    }

    private BudgetResponse toBudgetResponse(GoalWeekBudget b) { return new BudgetResponse(b.getId(), "%04d-W%02d".formatted(b.getIsoYear(), b.getIsoWeek()), b.getHours()); }
    private MetricResponse toMetricResponse(GoalMetric m) { return new MetricResponse(m.getId(), m.getKind(), m.getValue(), m.getTargetValue(), m.getAt()); }
    private String normalize(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
    private String currentIsoWeek() { java.time.LocalDate now = java.time.LocalDate.now(); return "%04d-W%02d".formatted(now.get(java.time.temporal.WeekFields.ISO.weekBasedYear()), now.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())); }

    @Data @NoArgsConstructor @AllArgsConstructor public static class GoalResponse { private Long id; private String title; private String description; private Integer priority; private Boolean archived; }
    @Data @NoArgsConstructor @AllArgsConstructor public static class GoalDetailResponse { private GoalResponse goal; private List<ProjectResponse> projects; private List<MetricResponse> metrics; private BudgetResponse budget; private FactResponse fact; }
    @Data @NoArgsConstructor @AllArgsConstructor public static class ProjectResponse { private Long id; private String title; }
    @Data @NoArgsConstructor @AllArgsConstructor public static class BudgetResponse { private Long id; private String week; private BigDecimal hours; }
    @Data @NoArgsConstructor @AllArgsConstructor public static class MetricResponse { private Long id; private String kind; private BigDecimal value; private BigDecimal targetValue; private LocalDateTime at; }
    @Data @NoArgsConstructor @AllArgsConstructor public static class FactResponse { private String week; private BigDecimal hours; }
    @Data @NoArgsConstructor @AllArgsConstructor public static class CreateGoalRequest { @NotBlank @Size(max = 200) private String title; @Size(max = 10000) private String description; @Positive private Integer priority; }
    @Data @NoArgsConstructor @AllArgsConstructor public static class UpdateGoalRequest { @NotBlank @Size(max = 200) private String title; @Size(max = 10000) private String description; @Positive private Integer priority; }
    @Data @NoArgsConstructor @AllArgsConstructor public static class PriorityRequest { @NotNull @Positive private Integer priority; }
    @Data @NoArgsConstructor @AllArgsConstructor public static class BudgetRequest { @NotBlank private String week; @NotNull @DecimalMin(value = "0.0", inclusive = true) private BigDecimal hours; }
    @Data @NoArgsConstructor @AllArgsConstructor public static class MetricRequest { @NotBlank @Size(max = 100) private String kind; @NotNull private BigDecimal value; private BigDecimal targetValue; private LocalDateTime at; public MetricRequest(String kind, BigDecimal value, LocalDateTime at) { this(kind, value, null, at); } }
    @Data @NoArgsConstructor @AllArgsConstructor public static class IncrementRequest { @NotNull private BigDecimal amount; }

}
