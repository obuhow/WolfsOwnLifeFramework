package ru.wolf.api.goal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.wolf.api.user.User;
import java.util.Optional;
import java.util.List;

public interface GoalWeekBudgetRepository extends JpaRepository<GoalWeekBudget, Long> {
    Optional<GoalWeekBudget> findByGoalIdAndIsoYearAndIsoWeek(Long goalId, Integer isoYear, Integer isoWeek);
    List<GoalWeekBudget> findByGoalIdOrderByIsoYearDescIsoWeekDesc(Long goalId);
    List<GoalWeekBudget> findByGoalIdInAndIsoYearAndIsoWeek(List<Long> goalIds, Integer isoYear, Integer isoWeek);

    @Modifying
    @Query("DELETE FROM GoalWeekBudget b WHERE b.goal.user = :user")
    void deleteAllByUser(@Param("user") User user);
}
