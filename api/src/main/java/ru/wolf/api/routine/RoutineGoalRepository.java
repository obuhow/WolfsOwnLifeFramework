package ru.wolf.api.routine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.wolf.api.user.User;

import java.util.List;

public interface RoutineGoalRepository extends JpaRepository<RoutineGoal, RoutineGoalId> {
    @Query("select rg from RoutineGoal rg join fetch rg.goal where rg.routine.id = :routineId order by rg.goal.priority asc")
    List<RoutineGoal> findByRoutineId(@Param("routineId") Long routineId);

    /**
     * Связка Ритм↔Цель своего user_id не имеет — чистится через владельца Ритма.
     * См. {@code UserPurgeService}.
     */
    @Modifying
    @Query("delete from RoutineGoal rg where rg.routine.id in (select r.id from Routine r where r.user = :user)")
    void deleteAllByUser(@Param("user") User user);
}
