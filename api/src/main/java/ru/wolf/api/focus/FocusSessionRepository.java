package ru.wolf.api.focus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.wolf.api.user.User;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

public interface FocusSessionRepository extends JpaRepository<FocusSession, Long> {
    Optional<FocusSession> findFirstByUserAndEndedAtIsNullOrderByStartedAtDesc(User user);
    List<FocusSession> findByUserAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtAsc(User user, LocalDateTime from, LocalDateTime to);

    @Query("SELECT s FROM FocusSession s WHERE s.user = :user AND s.id = :id")
    Optional<FocusSession> findByUserAndId(@Param("user") User user, @Param("id") Long id);
}
