package ru.wolf.api.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.wolf.api.user.User;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByPlanFrozenAtBeforeOrPlanFrozenAtIsNull(LocalDate date);

    @Query("""
            SELECT DISTINCT p FROM Project p
            JOIN FETCH p.lifeArea
            LEFT JOIN FETCH p.parent
            WHERE p.user = :user
            ORDER BY p.title ASC
            """)
    List<Project> findByUserOrderByTitleAsc(@Param("user") User user);

    @Query("""
            SELECT DISTINCT p FROM Project p
            JOIN FETCH p.lifeArea
            LEFT JOIN FETCH p.parent
            WHERE p.user = :user AND p.lifeArea.id = :lifeAreaId
            ORDER BY p.title ASC
            """)
    List<Project> findByUserAndLifeAreaIdOrderByTitleAsc(
            @Param("user") User user,
            @Param("lifeAreaId") Long lifeAreaId
    );

    @Query("""
            SELECT p FROM Project p
            JOIN FETCH p.lifeArea
            LEFT JOIN FETCH p.parent
            WHERE p.user = :user AND p.id = :id
            """)
    Optional<Project> findByUserAndId(@Param("user") User user, @Param("id") Long id);

    @Query("""
            SELECT p FROM Project p
            WHERE p.user = :user AND p.id IN :ids
            """)
    List<Project> findByUserAndIdIn(@Param("user") User user, @Param("ids") List<Long> ids);

    @Query("""
            SELECT p FROM Project p
            JOIN FETCH p.lifeArea
            LEFT JOIN FETCH p.parent
            WHERE p.user = :user AND LOWER(p.title) = LOWER(:title)
            """)
    Optional<Project> findByUserAndTitleIgnoreCase(@Param("user") User user, @Param("title") String title);

    void deleteAllByUser(User user);
}