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
            WHERE te.user.id = :userId AND te.endAt = :endAt
            """)
    Optional<TimeEntry> findByUserIdAndEndAt(@Param("userId") Long userId,
                                             @Param("endAt") LocalDateTime endAt);

    /** Entry that covers slot: startAt <= slotStart < endAt */
    @Query("""
            SELECT te FROM TimeEntry te
            LEFT JOIN FETCH te.delo
            WHERE te.user.id = :userId
              AND te.startAt <= :slotStart
              AND te.endAt > :slotStart
            """)
    Optional<TimeEntry> findCoveringSlot(@Param("userId") Long userId,
                                         @Param("slotStart") LocalDateTime slotStart);

    /** Intervals overlapping half-open [from, to): start < to AND end > from */
    @Query("""
            SELECT te FROM TimeEntry te
            LEFT JOIN FETCH te.delo
            WHERE te.user.id = :userId
              AND te.startAt < :to
              AND te.endAt > :from
            ORDER BY te.startAt ASC
            """)
    List<TimeEntry> findOverlapping(@Param("userId") Long userId,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

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
              AND te.startAt < :to
              AND te.endAt > :from
            ORDER BY te.startAt ASC
            """)
    List<TimeEntry> findByUserIdAndStatusOverlapping(
            @Param("userId") Long userId,
            @Param("status") TimeEntry.Status status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    void deleteByUserIdAndStartAt(Long userId, LocalDateTime startAt);

    @Query("""
            SELECT te FROM TimeEntry te
            LEFT JOIN FETCH te.delo
            WHERE te.user.id = :userId AND te.delo.id = :deloId
              AND te.startAt >= :from AND te.startAt < :to
            ORDER BY te.startAt ASC
            """)
    List<TimeEntry> findByUserAndDeloAndStartAtBetween(@Param("userId") Long userId, @Param("deloId") Long deloId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM TimeEntry te WHERE te.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
