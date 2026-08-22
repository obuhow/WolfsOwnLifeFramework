package ru.wolf.api.focus;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.wolf.api.user.User;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

public interface FocusSessionRepository extends JpaRepository<FocusSession, Long> {
    Optional<FocusSession> findFirstByUserAndEndedAtIsNullOrderByStartedAtDesc(User user);
    List<FocusSession> findByUserAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtAsc(User user, LocalDateTime from, LocalDateTime to);
}
