package ru.wolf.api.lifearea;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.wolf.api.user.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface LifeAreaRepository extends JpaRepository<LifeArea, Long> {

    List<LifeArea> findByUserOrderBySortOrderAscNameAsc(User user);

    Optional<LifeArea> findByUserAndId(User user, Long id);

    Optional<LifeArea> findFirstByUserOrderBySortOrderAsc(User user);

    @Query("SELECT COALESCE(MAX(la.sortOrder), -1) FROM LifeArea la WHERE la.user = :user")
    int findMaxSortOrderByUser(@Param("user") User user);

    boolean existsByUserAndName(User user, String name);

    void deleteAllByUser(User user);
}