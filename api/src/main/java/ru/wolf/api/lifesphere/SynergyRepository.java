package ru.wolf.api.lifesphere;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.wolf.api.project.Project;
import ru.wolf.api.user.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface SynergyRepository extends JpaRepository<Synergy, Long> {

    List<Synergy> findByUser(User user);

    @Query("SELECT s FROM Synergy s LEFT JOIN FETCH s.sphere WHERE s.user = :user AND s.project = :project")
    List<Synergy> findByUserAndProjectWithSphere(@Param("user") User user, @Param("project") Project project);

    @Query("SELECT s FROM Synergy s LEFT JOIN FETCH s.sphere WHERE s.user = :user AND s.ideaId = :ideaId")
    List<Synergy> findByUserAndIdeaIdWithSphere(@Param("user") User user, @Param("ideaId") Long ideaId);

    @Query("SELECT s FROM Synergy s LEFT JOIN FETCH s.sphere WHERE s.user = :user")
    List<Synergy> findByUserWithSphere(@Param("user") User user);

    Optional<Synergy> findByUserAndProjectAndSphere(User user, Project project, LifeSphere sphere);

    Optional<Synergy> findByUserAndIdeaIdAndSphere(User user, Long ideaId, LifeSphere sphere);

    boolean existsByUserAndProjectAndSphere(User user, Project project, LifeSphere sphere);

    boolean existsByUserAndIdeaIdAndSphere(User user, Long ideaId, LifeSphere sphere);
}
