/*
 * WOLF — Wolf's Own Life Framework
 * Copyright (C) 2025 Pavel Obukhov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
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

    boolean existsByUserAndRoutineAndSphere(User user, ru.wolf.api.routine.Routine routine, LifeSphere sphere);

    @Query("SELECT s FROM Synergy s WHERE s.user = :user AND s.id = :id")
    Optional<Synergy> findByUserAndId(@Param("user") User user, @Param("id") Long id);

    void deleteAllByUser(User user);
}
