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
package ru.wolf.api.routine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.wolf.api.user.User;

import java.util.List;

public interface RoutineGoalRepository extends JpaRepository<RoutineGoal, RoutineGoalId> {
    @Query("select rg from RoutineGoal rg join fetch rg.goal where rg.routine.id = :routineId order by rg.goal.priority asc")
    List<RoutineGoal> findByRoutineId(@Param("routineId") Long routineId);

    /**
     * Связка Ритм↔Цель своего user_id не имеет — чистится через владельца Ритма.
     * См. {@code UserPurgeService}.
     */
    @Modifying
    @Query("delete from RoutineGoal rg where rg.routine.id in (select r.id from Routine r where r.user = :user)")
    void deleteAllByUser(@Param("user") User user);
}
