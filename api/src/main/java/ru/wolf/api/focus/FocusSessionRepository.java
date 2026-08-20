package ru.wolf.api.focus;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.wolf.api.user.User;
import java.util.Optional;

public interface FocusSessionRepository extends JpaRepository<FocusSession, Long> {
    Optional<FocusSession> findFirstByUserAndEndedAtIsNullOrderByStartedAtDesc(User user);
}
