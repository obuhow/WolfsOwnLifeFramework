package ru.wolf.api.goal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.wolf.api.project.Project;
import ru.wolf.api.user.User;
import java.util.List;

public interface GoalProjectRepository extends JpaRepository<GoalProject, GoalProjectId> {
    @Query("SELECT gp FROM GoalProject gp JOIN FETCH gp.project WHERE gp.goal.id = :goalId ORDER BY gp.project.title ASC")
    List<GoalProject> findByGoalId(@Param("goalId") Long goalId);

    boolean existsByGoalIdAndProjectId(Long goalId, Long projectId);

    @Query("SELECT gp FROM GoalProject gp WHERE gp.goal.id = :goalId")
    List<GoalProject> findLinks(@Param("goalId") Long goalId);

    @Modifying
    @Query("DELETE FROM GoalProject gp WHERE gp.goal.user = :user")
    void deleteAllByUser(@Param("user") User user);
}
