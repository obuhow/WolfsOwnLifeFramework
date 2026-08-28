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
import java.util.Optional;
import java.time.LocalDate;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByPlanFrozenAtBeforeOrPlanFrozenAtIsNull(LocalDate date);

    @Query("""
            SELECT DISTINCT p FROM Project p
            JOIN FETCH p.lifeArea
            LEFT JOIN FETCH p.parent
            WHERE p.user = :user
            ORDER BY p.title ASC
            """)
    List<Project> findByUserOrderByTitleAsc(@Param("user") User user);

    @Query("""
            SELECT DISTINCT p FROM Project p
            JOIN FETCH p.lifeArea
            LEFT JOIN FETCH p.parent
            WHERE p.user = :user AND p.status = ru.wolf.api.project.Project.Status.IN_PROGRESS
            ORDER BY p.title ASC
            """)
    List<Project> findInProgressByUserOrderByTitleAsc(@Param("user") User user);

    @Query("""
            SELECT DISTINCT p FROM Project p
            JOIN FETCH p.lifeArea
            LEFT JOIN FETCH p.parent
            WHERE p.user = :user AND p.lifeArea.id = :lifeAreaId
            ORDER BY p.title ASC
            """)
    List<Project> findByUserAndLifeAreaIdOrderByTitleAsc(
            @Param("user") User user,
            @Param("lifeAreaId") Long lifeAreaId
    );

    @Query("""
            SELECT p FROM Project p
            JOIN FETCH p.lifeArea
            LEFT JOIN FETCH p.parent
            WHERE p.user = :user AND p.id = :id
            """)
    Optional<Project> findByUserAndId(@Param("user") User user, @Param("id") Long id);

    @Query("""
            SELECT p FROM Project p
            WHERE p.user = :user AND p.id IN :ids
            """)
    List<Project> findByUserAndIdIn(@Param("user") User user, @Param("ids") List<Long> ids);

    @Query("""
            SELECT p FROM Project p
            JOIN FETCH p.lifeArea
            LEFT JOIN FETCH p.parent
            WHERE p.user = :user AND LOWER(p.title) = LOWER(:title)
            """)
    Optional<Project> findByUserAndTitleIgnoreCase(@Param("user") User user, @Param("title") String title);

    void deleteAllByUser(User user);
}