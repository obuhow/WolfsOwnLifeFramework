package ru.wolf.api.gantt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.wolf.api.user.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface WeekPlanRepository extends JpaRepository<WeekPlan, Long> {

    @Query("""
            SELECT wp FROM WeekPlan wp
            JOIN FETCH wp.project
            WHERE wp.user = :user
              AND (
                    wp.isoYear > :fromYear
                    OR (wp.isoYear = :fromYear AND wp.isoWeek >= :fromWeek)
                  )
              AND (
                    wp.isoYear < :toYear
                    OR (wp.isoYear = :toYear AND wp.isoWeek <= :toWeek)
                  )
            """)
    List<WeekPlan> findInWeekRange(
            @Param("user") User user,
            @Param("fromYear") int fromYear,
            @Param("fromWeek") int fromWeek,
            @Param("toYear") int toYear,
            @Param("toWeek") int toWeek
    );

    Optional<WeekPlan> findByUserAndProjectIdAndIsoYearAndIsoWeek(
            User user, Long projectId, Integer isoYear, Integer isoWeek
    );

    void deleteByUserAndProjectIdAndIsoYearAndIsoWeek(
            User user, Long projectId, Integer isoYear, Integer isoWeek
    );
}
