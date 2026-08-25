package ru.wolf.api.loadcurve;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Точки кривой нагрузки принадлежат Проекту или Ритму пользователя — своего
     * user_id у них нет. Удаляются через владельца; см. {@code UserPurgeService}.
     */
    @Modifying
    @Query("delete from LoadCurveEntry e where e.project.id in (select p.id from Project p where p.user = :user) or e.routine.id in (select r.id from Routine r where r.user = :user)")
    void deleteAllByUser(@Param("user") User user);
}
