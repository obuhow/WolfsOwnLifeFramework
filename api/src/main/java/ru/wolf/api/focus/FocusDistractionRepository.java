package ru.wolf.api.focus;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FocusDistractionRepository extends JpaRepository<FocusDistraction, Long> {
    List<FocusDistraction> findBySessionIdOrderByAtAsc(Long sessionId);
}
