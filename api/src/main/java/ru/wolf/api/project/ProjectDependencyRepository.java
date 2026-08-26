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
package ru.wolf.api.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.wolf.api.user.User;

import java.util.List;

@Repository
public interface ProjectDependencyRepository extends JpaRepository<ProjectDependency, ProjectDependencyId> {

    @Query("""
            SELECT d FROM ProjectDependency d
            JOIN FETCH d.blocker
            JOIN FETCH d.blocked
            WHERE d.user = :user AND d.blocked.id = :projectId
            ORDER BY d.blocker.title ASC
            """)
    List<ProjectDependency> findBlockedBy(@Param("user") User user, @Param("projectId") Long projectId);

    @Query("""
            SELECT d FROM ProjectDependency d
            JOIN FETCH d.blocker
            JOIN FETCH d.blocked
            WHERE d.user = :user AND d.blocker.id = :projectId
            ORDER BY d.blocked.title ASC
            """)
    List<ProjectDependency> findBlocks(@Param("user") User user, @Param("projectId") Long projectId);

    @Query("""
            SELECT d FROM ProjectDependency d
            JOIN FETCH d.blocker
            JOIN FETCH d.blocked
            WHERE d.user = :user
            """)
    List<ProjectDependency> findAllForUser(@Param("user") User user);

    boolean existsByUserAndBlockerIdAndBlockedId(User user, Long blockerId, Long blockedId);

    void deleteAllByUser(User user);
}
