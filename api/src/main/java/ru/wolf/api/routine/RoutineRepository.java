package ru.wolf.api.routine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.wolf.api.user.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoutineRepository extends JpaRepository<Routine, Long> {
    List<Routine> findByUserAndArchivedOrderByTitleAsc(User user, boolean archived);

    @Query("select r from Routine r where r.user = :user and r.id = :id")
    Optional<Routine> findByUserAndId(@Param("user") User user, @Param("id") Long id);
}
