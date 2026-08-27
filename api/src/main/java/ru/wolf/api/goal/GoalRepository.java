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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.wolf.api.user.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findByUserAndArchivedOrderByPriorityAsc(User user, Boolean archived);

    @Query("SELECT g FROM Goal g WHERE g.user = :user AND g.id = :id")
    Optional<Goal> findByUserAndId(@Param("user") User user, @Param("id") Long id);

    boolean existsByUserAndPriority(User user, Integer priority);

    @Query("SELECT COALESCE(MAX(g.priority), 0) FROM Goal g WHERE g.user = :user AND g.archived = false")
    int findMaxActivePriority(@Param("user") User user);

    void deleteAllByUser(User user);
}
