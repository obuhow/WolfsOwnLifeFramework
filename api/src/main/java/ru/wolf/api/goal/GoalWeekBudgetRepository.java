package ru.wolf.api.goal;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface GoalWeekBudgetRepository extends JpaRepository<GoalWeekBudget, Long> {
    Optional<GoalWeekBudget> findByGoalIdAndIsoYearAndIsoWeek(Long goalId, Integer isoYear, Integer isoWeek);
    List<GoalWeekBudget> findByGoalIdOrderByIsoYearDescIsoWeekDesc(Long goalId);
    List<GoalWeekBudget> findByGoalIdInAndIsoYearAndIsoWeek(List<Long> goalIds, Integer isoYear, Integer isoWeek);
}
