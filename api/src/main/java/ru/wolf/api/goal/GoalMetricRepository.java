package ru.wolf.api.goal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.wolf.api.user.User;
import java.util.List;

public interface GoalMetricRepository extends JpaRepository<GoalMetric, Long> {
    @Query("SELECT m FROM GoalMetric m WHERE m.goal.id = :goalId ORDER BY m.at DESC, m.id DESC")
    List<GoalMetric> findByGoalIdOrderByAtDesc(@Param("goalId") Long goalId);

    @Modifying
    @Query("DELETE FROM GoalMetric m WHERE m.goal.user = :user")
    void deleteAllByUser(@Param("user") User user);
}
