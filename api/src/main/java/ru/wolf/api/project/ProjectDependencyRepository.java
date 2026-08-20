package ru.wolf.api.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.wolf.api.user.User;

import java.util.List;

@Repository
public interface ProjectDependencyRepository extends JpaRepository<ProjectDependency, ProjectDependencyId> {

    @Query("""
            SELECT d FROM ProjectDependency d
            JOIN FETCH d.blocker
            JOIN FETCH d.blocked
            WHERE d.user = :user AND d.blocked.id = :projectId
            ORDER BY d.blocker.title ASC
            """)
    List<ProjectDependency> findBlockedBy(@Param("user") User user, @Param("projectId") Long projectId);

    @Query("""
            SELECT d FROM ProjectDependency d
            JOIN FETCH d.blocker
            JOIN FETCH d.blocked
            WHERE d.user = :user AND d.blocker.id = :projectId
            ORDER BY d.blocked.title ASC
            """)
    List<ProjectDependency> findBlocks(@Param("user") User user, @Param("projectId") Long projectId);

    @Query("""
            SELECT d FROM ProjectDependency d
            JOIN FETCH d.blocker
            JOIN FETCH d.blocked
            WHERE d.user = :user
            """)
    List<ProjectDependency> findAllForUser(@Param("user") User user);

    boolean existsByUserAndBlockerIdAndBlockedId(User user, Long blockerId, Long blockedId);
}
