package ru.wolf.api.routine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RoutineGoalRepository extends JpaRepository<RoutineGoal, RoutineGoalId> {
    @Query("select rg from RoutineGoal rg join fetch rg.goal where rg.routine.id = :routineId order by rg.goal.priority asc")
    List<RoutineGoal> findByRoutineId(@Param("routineId") Long routineId);
}
