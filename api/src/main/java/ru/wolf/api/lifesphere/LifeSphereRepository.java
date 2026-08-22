package ru.wolf.api.lifesphere;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.wolf.api.user.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface LifeSphereRepository extends JpaRepository<LifeSphere, Long> {

    List<LifeSphere> findByUserOrderBySortOrderAscNameAsc(User user);

    Optional<LifeSphere> findByUserAndId(User user, Long id);

    @Query("SELECT COALESCE(MAX(ls.sortOrder), -1) FROM LifeSphere ls WHERE ls.user = :user")
    int findMaxSortOrderByUser(@Param("user") User user);

    boolean existsByUserAndName(User user, String name);

    Optional<LifeSphere> findByUserAndName(User user, String name);

    void deleteAllByUser(User user);
}