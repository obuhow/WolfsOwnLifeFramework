package ru.wolf.api.backlog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.wolf.api.user.User;
import java.util.List;
import java.util.Optional;

public interface BacklogItemRepository extends JpaRepository<BacklogItem, Long> {
    @Query("select b from BacklogItem b join fetch b.delo where b.user = :user and b.scope = :scope and b.periodId = :period order by b.position asc, b.id asc")
    List<BacklogItem> findPeriod(@Param("user") User user, @Param("scope") BacklogItem.Scope scope, @Param("period") String period);
    Optional<BacklogItem> findByUserAndDeloIdAndScopeAndPeriodId(User user, Long deloId, BacklogItem.Scope scope, String periodId);
    int countByUserAndScope(User user, BacklogItem.Scope scope);
    
    @Query("SELECT b FROM BacklogItem b WHERE b.user = :user AND b.id = :id")
    Optional<BacklogItem> findByUserAndId(@Param("user") User user, @Param("id") Long id);
}
