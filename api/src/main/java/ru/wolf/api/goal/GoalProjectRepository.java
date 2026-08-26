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
package ru.wolf.api.goal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.wolf.api.project.Project;
import ru.wolf.api.user.User;
import java.util.List;

public interface GoalProjectRepository extends JpaRepository<GoalProject, GoalProjectId> {
    @Query("SELECT gp FROM GoalProject gp JOIN FETCH gp.project WHERE gp.goal.id = :goalId ORDER BY gp.project.title ASC")
    List<GoalProject> findByGoalId(@Param("goalId") Long goalId);

    boolean existsByGoalIdAndProjectId(Long goalId, Long projectId);

    @Query("SELECT gp FROM GoalProject gp WHERE gp.goal.id = :goalId")
    List<GoalProject> findLinks(@Param("goalId") Long goalId);

    @Modifying
    @Query("DELETE FROM GoalProject gp WHERE gp.goal.user = :user")
    void deleteAllByUser(@Param("user") User user);
}
