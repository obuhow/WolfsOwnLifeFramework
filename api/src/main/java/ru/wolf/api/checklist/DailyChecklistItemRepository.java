package ru.wolf.api.checklist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.wolf.api.user.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyChecklistItemRepository extends JpaRepository<DailyChecklistItem, Long> {
    @Query("select c from DailyChecklistItem c left join fetch c.delo where c.user = :user and c.date = :date order by c.position asc, c.id asc")
    List<DailyChecklistItem> findForDate(@Param("user") User user, @Param("date") LocalDate date);
    Optional<DailyChecklistItem> findByIdAndUser(Long id, User user);
}
