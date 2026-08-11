package ru.wolf.api.timeentry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {

    @Query("""
            SELECT te FROM TimeEntry te
            LEFT JOIN FETCH te.delo
            WHERE te.user.id = :userId AND te.startAt = :startAt
            """)
    Optional<TimeEntry> findByUserIdAndStartAt(@Param("userId") Long userId,
                                               @Param("startAt") LocalDateTime startAt);

    @Query("""
            SELECT te FROM TimeEntry te
            LEFT JOIN FETCH te.delo
            WHERE te.user.id = :userId
              AND te.startAt >= :start
              AND te.startAt < :end
            ORDER BY te.startAt ASC
            """)
    List<TimeEntry> findByUserIdAndStartAtBetween(@Param("userId") Long userId,
                                                   @Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end);

    @Query("""
            SELECT te FROM TimeEntry te
            LEFT JOIN FETCH te.delo
            WHERE te.user.id = :userId
              AND te.status = :status
              AND te.startAt >= :start
              AND te.startAt < :end
            ORDER BY te.startAt ASC
            """)
    List<TimeEntry> findByUserIdAndStatusAndStartAtBetween(
            @Param("userId") Long userId,
            @Param("status") TimeEntry.Status status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    void deleteByUserIdAndStartAt(Long userId, LocalDateTime startAt);
}
