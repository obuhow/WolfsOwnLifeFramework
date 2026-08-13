package ru.wolf.api.backlog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.wolf.api.user.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface WeekBacklogRepository extends JpaRepository<WeekBacklog, Long> {

    @Query("""
            SELECT wb FROM WeekBacklog wb
            LEFT JOIN FETCH wb.delos
            WHERE wb.user = :user AND wb.isoYear = :isoYear AND wb.isoWeek = :isoWeek
            """)
    Optional<WeekBacklog> findByUserAndIsoYearAndIsoWeek(
            @Param("user") User user,
            @Param("isoYear") Integer isoYear,
            @Param("isoWeek") Integer isoWeek);

    @Query("""
            SELECT wb FROM WeekBacklog wb
            LEFT JOIN FETCH wb.delos
            WHERE wb.user = :user
            ORDER BY wb.isoYear DESC, wb.isoWeek DESC
            """)
    List<WeekBacklog> findByUserOrderByYearDescWeekDesc(@Param("user") User user);

    void deleteByUserAndIsoYearAndIsoWeek(User user, Integer isoYear, Integer isoWeek);
}