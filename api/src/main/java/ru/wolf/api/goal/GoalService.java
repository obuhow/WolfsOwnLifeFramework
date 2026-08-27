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
 * along with this program. If not see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.goal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.goal.dto.*;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepository;
    private final GoalMetricRepository metricRepository;
    private final GoalWeekBudgetRepository budgetRepository;
    private final GoalProjectRepository goalProjectRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final GoalFactService goalFactService;

    @Transactional(readOnly = true)
    public List<GoalResponse> list(String username, boolean includeArchived) {
        User user = currentUser(username);
        return goalRepository.findByUserAndArchivedOrderByPriorityAsc(user, includeArchived)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public GoalDetailResponse get(String username, Long id, String week) {
        User user = currentUser(username);
        Goal goal = findGoal(user, id);
        String requestedWeek = week == null || week.isBlank() ? currentIsoWeek() : week;
        GoalFactService.IsoWeek parsed = goalFactService.parseWeek(requestedWeek);
        return toDetail(goal, requestedWeek, parsed);
    }

    @Transactional
    public GoalResponse create(String username, CreateGoalRequest request) {
        User user = currentUser(username);
        int priority = request.priority() == null ? goalRepository.findMaxActivePriority(user) + 1 : request.priority();
        if (priority < 1) {
            throw new IllegalArgumentException("Приоритет должен быть положительным");
        }
        if (request.priority() != null && goalRepository.existsByUserAndPriority(user, priority)) {
            throw new DuplicateGoalPriorityException(priority);
        }
        Goal goal = Goal.builder()
                .user(user)
                .title(request.title().trim())
                .description(normalize(request.description()))
                .priority(priority)
                .archived(false)
                .build();
        return toResponse(goalRepository.save(goal));
    }

    @Transactional
    public GoalResponse update(String username, Long id, UpdateGoalRequest request) {
        User user = currentUser(username);
        Goal goal = findGoal(user, id);
        if (request.priority() != null && !request.priority().equals(goal.getPriority())) {
            reorder(user, id, request.priority());
            goal = findGoal(user, id);
        }
        goal.setTitle(request.title().trim());
        goal.setDescription(normalize(request.description()));
        return toResponse(goalRepository.save(goal));
    }

    @Transactional
    public void archive(String username, Long id) {
        User user = currentUser(username);
        Goal goal = findGoal(user, id);
        goal.setArchived(true);
        goalRepository.save(goal);
    }

    @Transactional
    public List<GoalResponse> changePriority(String username, Long id, PriorityRequest request) {
        User user = currentUser(username);
        findGoal(user, id);
        reorder(user, id, request.priority());
        return goalRepository.findByUserAndArchivedOrderByPriorityAsc(user, false)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public BudgetResponse saveBudget(String username, Long id, BudgetRequest request) {
        User user = currentUser(username);
        Goal goal = findGoal(user, id);
        GoalFactService.IsoWeek week = goalFactService.parseWeek(request.week());
        GoalWeekBudget budget = budgetRepository.findByGoalIdAndIsoYearAndIsoWeek(id, week.year(), week.week())
                .orElseGet(() -> GoalWeekBudget.builder().goal(goal).isoYear(week.year()).isoWeek(week.week()).build());
        budget.setHours(request.hours().setScale(2, RoundingMode.HALF_UP));
        return toBudgetResponse(budgetRepository.save(budget));
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> budgets(String username, Long id) {
        User user = currentUser(username);
        findGoal(user, id);
        return budgetRepository.findByGoalIdOrderByIsoYearDescIsoWeekDesc(id)
                .stream().map(this::toBudgetResponse).toList();
    }

    @Transactional
    public MetricResponse addMetric(String username, Long id, MetricRequest request) {
        User user = currentUser(username);
        Goal goal = findGoal(user, id);
        GoalMetric metric = GoalMetric.builder()
                .goal(goal)
                .kind(request.kind().trim())
                .value(request.value())
                .targetValue(request.targetValue())
                .at(request.at() == null ? LocalDateTime.now() : request.at())
                .build();
        return toMetricResponse(metricRepository.save(metric));
    }

    @Transactional(readOnly = true)
    public List<MetricResponse> metrics(String username, Long id) {
        User user = currentUser(username);
        findGoal(user, id);
        return metricRepository.findByGoalIdOrderByAtDesc(id).stream()
                .map(this::toMetricResponse).toList();
    }

    @Transactional
    public MetricResponse incrementMetric(String username, Long id, Long metricId, IncrementRequest request) {
        User user = currentUser(username);
        Goal goal = findGoal(user, id);
        GoalMetric metric = metricRepository.findById(metricId).filter(item -> item.getGoal().getId().equals(goal.getId())).orElseThrow(() -> new IllegalArgumentException("Метрика не найдена"));
        BigDecimal next = metric.getValue().add(request.amount());
        if (next.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("Значение метрики не может быть ниже нуля");
        if (metric.getTargetValue() != null && next.compareTo(metric.getTargetValue()) > 0) throw new IllegalArgumentException("Значение метрики не может превышать цель");
        metric.setValue(next);
        metric.setAt(LocalDateTime.now());
        return toMetricResponse(metricRepository.saveAndFlush(metric));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> projects(String username, Long id) {
        User user = currentUser(username);
        findGoal(user, id);
        return goalProjectRepository.findByGoalId(id).stream()
                .map(link -> new ProjectResponse(link.getProject().getId(), link.getProject().getTitle()))
                .toList();
    }

    @Transactional
    public void linkProject(String username, Long id, Long projectId) {
        User user = currentUser(username);
        Goal goal = findGoal(user, id);
        Project project = projectRepository.findByUserAndId(user, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Проект не найден"));
        if (!goalProjectRepository.existsByGoalIdAndProjectId(id, projectId)) {
            goalProjectRepository.save(GoalProject.builder()
                    .id(new GoalProjectId(id, projectId)).goal(goal).project(project).build());
        }
    }

    @Transactional
    public void unlinkProject(String username, Long id, Long projectId) {
        User user = currentUser(username);
        findGoal(user, id);
        goalProjectRepository.deleteById(new GoalProjectId(id, projectId));
    }

    @Transactional(readOnly = true)
    public FactResponse fact(String username, Long id, String week) {
        User user = currentUser(username);
        Goal goal = findGoal(user, id);
        GoalFactService.IsoWeek parsed = goalFactService.parseWeek(week);
        return new FactResponse(week, goalFactService.calculate(user, goal, week));
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

    private User currentUser(String username) {
        return userRepository.findByUsername(username)
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
}
