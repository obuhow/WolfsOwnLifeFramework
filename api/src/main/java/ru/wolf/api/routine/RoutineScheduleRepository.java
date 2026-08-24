package ru.wolf.api.routine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.wolf.api.user.User;

import java.util.List;

public interface RoutineScheduleRepository extends JpaRepository<RoutineSchedule, Long> {
    List<RoutineSchedule> findByRoutineIdOrderByDayOfWeekAscStartTimeAsc(Long routineId);

    @Modifying
    @Query("DELETE FROM RoutineSchedule rs WHERE rs.routine.user = :user")
    void deleteByUser(@Param("user") User user);
}
