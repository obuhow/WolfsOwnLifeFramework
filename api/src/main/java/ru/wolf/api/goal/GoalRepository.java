package ru.wolf.api.goal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.wolf.api.user.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findByUserAndArchivedOrderByPriorityAsc(User user, Boolean archived);

    @Query("SELECT g FROM Goal g WHERE g.user = :user AND g.id = :id")
    Optional<Goal> findByUserAndId(@Param("user") User user, @Param("id") Long id);

    boolean existsByUserAndPriority(User user, Integer priority);

    @Query("SELECT COALESCE(MAX(g.priority), 0) FROM Goal g WHERE g.user = :user AND g.archived = false")
    int findMaxActivePriority(@Param("user") User user);

    void deleteAllByUser(User user);
}
