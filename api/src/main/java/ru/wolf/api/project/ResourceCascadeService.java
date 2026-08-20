package ru.wolf.api.project;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.goal.Goal;
import ru.wolf.api.goal.GoalProjectRepository;
import ru.wolf.api.goal.GoalRepository;
import ru.wolf.api.goal.GoalWeekBudgetRepository;

import ru.wolf.api.user.User;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.Map;
import java.util.List;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class ResourceCascadeService {

    private final ProjectRepository projectRepository;
    private final GoalRepository goalRepository;
    private final GoalProjectRepository goalProjectRepository;
    private final GoalWeekBudgetRepository budgetRepository;


    @Transactional(readOnly = true)
    public Preview preview(User user, Long projectId, LocalDate newEnd) {
        Project project = projectRepository.findByUserAndId(user, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Проект не найден"));
        if (newEnd == null) {
            throw new IllegalArgumentException("Новая дата окончания обязательна");
        }
        if (project.getStartDate() != null && newEnd.isBefore(project.getStartDate())) {
            throw new IllegalArgumentException("Дата окончания не может быть раньше даты начала");
        }
        if (project.getEndDate() != null && !newEnd.isAfter(project.getEndDate())) {
            return emptyPreview(user);
        }

        BigDecimal available = value(user.getAvailableWeeklyHours());
        List<Goal> activeGoals = goalRepository.findByUserAndArchivedOrderByPriorityAsc(user, false);
        Map<Long, Goal> goalsById = activeGoals.stream().collect(java.util.stream.Collectors.toMap(Goal::getId, goal -> goal));
        List<Long> affectedGoalIds = activeGoals.stream()
                .filter(goal -> goalProjectRepository.existsByGoalIdAndProjectId(goal.getId(), projectId))
                .map(Goal::getId)
                .toList();

        LocalDate today = LocalDate.now(ZoneId.of(user.getTimezone()));
        LocalDate firstMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastMonday = newEnd.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<WeekLoad> loads = new java.util.ArrayList<>();
        for (LocalDate monday = firstMonday; !monday.isAfter(lastMonday); monday = monday.plusWeeks(1)) {
            WeekFields iso = WeekFields.ISO;
            int year = monday.get(iso.weekBasedYear());
            int week = monday.get(iso.weekOfWeekBasedYear());
            Map<Long, BigDecimal> budgets = budgetRepository
                    .findByGoalIdInAndIsoYearAndIsoWeek(activeGoals.stream().map(Goal::getId).toList(), year, week)
                    .stream()
                    .collect(java.util.stream.Collectors.toMap(
                            budget -> budget.getGoal().getId(),
                            budget -> value(budget.getHours())));
            BigDecimal total = activeGoals.stream()
                    .map(goal -> budgets.getOrDefault(goal.getId(), BigDecimal.ZERO.setScale(2)))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal deficit = total.subtract(available).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            loads.add(new WeekLoad(monday, budgets, total, deficit));
        }

        WeekLoad peak = loads.stream()
                .max(Comparator.comparing(WeekLoad::deficit).thenComparing(WeekLoad::monday))
                .orElse(null);
        if (peak == null || peak.deficit().signum() == 0) {
            return emptyPreview(user);
        }
        List<GoalPreview> goalPreviews = affectedGoalIds.stream()
                .map(goalsById::get)
                .map(goal -> toGoalPreview(goal, peak.budgets().getOrDefault(goal.getId(), BigDecimal.ZERO.setScale(2)), peak.total(), peak.deficit()))
                .filter(item -> item.currentBudget().signum() > 0 || item.deficit().signum() > 0)
                .toList();
        return new Preview(available, goalPreviews, peak.deficit());
    }

    private Preview emptyPreview(User user) {
        return new Preview(value(user.getAvailableWeeklyHours()), List.of(), BigDecimal.ZERO.setScale(2));
    }

    private GoalPreview toGoalPreview(Goal goal, BigDecimal current, BigDecimal total, BigDecimal totalDeficit) {
        BigDecimal share = total.signum() == 0
                ? BigDecimal.ZERO
                : totalDeficit.multiply(current).divide(total, 2, RoundingMode.HALF_UP);
        BigDecimal required = current.add(share).setScale(2, RoundingMode.HALF_UP);
        return new GoalPreview(goal.getId(), goal.getTitle(), current, required, share);
    }


    private BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private record WeekLoad(LocalDate monday, Map<Long, BigDecimal> budgets, BigDecimal total, BigDecimal deficit) {}

    public record Preview(
            BigDecimal availableWeeklyHours,
            List<GoalPreview> affectedGoals,
            BigDecimal totalDeficit
    ) {}

    public record GoalPreview(
            Long goalId,
            String title,
            BigDecimal currentBudget,
            BigDecimal requiredBudget,
            BigDecimal deficit
    ) {}
}
