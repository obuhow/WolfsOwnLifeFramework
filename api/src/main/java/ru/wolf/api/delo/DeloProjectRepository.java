package ru.wolf.api.delo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.wolf.api.project.Project;
import ru.wolf.api.user.User;

import java.util.List;

@Repository
public interface DeloProjectRepository extends JpaRepository<DeloProject, DeloProjectId> {

    boolean existsByDeloAndProject(Delo delo, Project project);

    @Query("""
            SELECT dp FROM DeloProject dp
            JOIN FETCH dp.delo
            JOIN FETCH dp.project
            WHERE dp.project.id = :projectId
            ORDER BY dp.delo.title ASC
            """)
    List<DeloProject> findByProjectId(@Param("projectId") Long projectId);

    @Query("""
            SELECT dp FROM DeloProject dp
            JOIN FETCH dp.project
            WHERE dp.delo.id = :deloId
            ORDER BY dp.project.title ASC
            """)
    List<DeloProject> findByDeloId(@Param("deloId") Long deloId);

    /**
     * Связка Дело↔Проект своего user_id не имеет — чистится через владельца Дела.
     * См. {@code UserPurgeService}.
     */
    @Modifying
    @Query("DELETE FROM DeloProject dp WHERE dp.delo.id IN (SELECT d.id FROM Delo d WHERE d.user = :user)")
    void deleteAllByUser(@Param("user") User user);
}
