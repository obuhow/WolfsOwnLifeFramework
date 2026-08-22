package ru.wolf.api.loadcurve;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.wolf.api.user.User;
import java.time.LocalDate;
import java.util.List;

public interface LoadCurveEntryRepository extends JpaRepository<LoadCurveEntry, Long> {
    @Query("select e from LoadCurveEntry e left join fetch e.project left join fetch e.routine where (e.project.user = :user or e.routine.user = :user) and e.weekStart >= :from and e.weekStart <= :to order by e.weekStart")
    List<LoadCurveEntry> findRange(@Param("user") User user, @Param("from") LocalDate from, @Param("to") LocalDate to);
    List<LoadCurveEntry> findByProjectIdOrderByWeekStart(Long projectId);
    List<LoadCurveEntry> findByRoutineIdOrderByWeekStart(Long routineId);
}
