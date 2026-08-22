package ru.wolf.api.routine;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoutineScheduleRepository extends JpaRepository<RoutineSchedule, Long> {
    List<RoutineSchedule> findByRoutineIdOrderByDayOfWeekAscStartTimeAsc(Long routineId);
}
