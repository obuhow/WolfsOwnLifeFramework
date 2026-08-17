package ru.wolf.api.delo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.wolf.api.user.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeloRepository extends JpaRepository<Delo, Long> {

    @Query("""
            SELECT DISTINCT d FROM Delo d
            LEFT JOIN FETCH d.deloProjects dp
            LEFT JOIN FETCH dp.project
            WHERE d.user = :user
            ORDER BY d.title ASC
            """)
    List<Delo> findByUserOrderByTitleAsc(@Param("user") User user);

    @Query("""
            SELECT DISTINCT d FROM Delo d
            LEFT JOIN FETCH d.deloProjects dp
            LEFT JOIN FETCH dp.project
            WHERE d.user = :user AND d.id = :id
            """)
    Optional<Delo> findByUserAndId(@Param("user") User user, @Param("id") Long id);

    Optional<Delo> findFirstByUserAndTitleIgnoreCaseOrderByIdAsc(User user, String title);

    @Query("""
            SELECT d FROM Delo d
            WHERE d.user = :user AND LOWER(d.title) IN :lowerTitles
            """)
    List<Delo> findByUserAndTitleInIgnoreCase(@Param("user") User user, @Param("lowerTitles") List<String> lowerTitles);
}
