package ru.wolf.api.focus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface FocusDistractionRepository extends JpaRepository<FocusDistraction, Long> {
    List<FocusDistraction> findBySessionIdOrderByAtAsc(Long sessionId);

    @Query("SELECT d FROM FocusDistraction d WHERE d.session.id = :sessionId AND d.id = :id")
    Optional<FocusDistraction> findBySessionIdAndId(@Param("sessionId") Long sessionId, @Param("id") Long id);
}
