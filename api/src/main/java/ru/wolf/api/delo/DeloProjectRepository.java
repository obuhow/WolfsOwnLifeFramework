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
package ru.wolf.api.delo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.wolf.api.project.Project;
import ru.wolf.api.user.User;

import java.util.List;

@Repository
public interface DeloProjectRepository extends JpaRepository<DeloProject, DeloProjectId> {

    boolean existsByDeloAndProject(Delo delo, Project project);

    @Query("""
            SELECT dp FROM DeloProject dp
            JOIN FETCH dp.delo
            JOIN FETCH dp.project
            WHERE dp.project.id = :projectId
            ORDER BY dp.delo.title ASC
            """)
    List<DeloProject> findByProjectId(@Param("projectId") Long projectId);

    @Query("SELECT dp FROM DeloProject dp JOIN FETCH dp.project WHERE dp.delo.id = :deloId ORDER BY dp.project.title ASC")
    List<DeloProject> findByDeloId(@Param("deloId") Long deloId);

    /** Число Дел проекта с учётом режима учёта часов (release 0.8). */
    @Query("SELECT COUNT(dp) FROM DeloProject dp WHERE dp.project.id = :projectId")
    long countByProjectId(@Param("projectId") Long projectId);

    @Query("SELECT COUNT(dp) FROM DeloProject dp WHERE dp.project.id = :projectId AND dp.isPrimary = true")
    long countPrimaryByProjectId(@Param("projectId") Long projectId);

    /**
     * Связка Дело↔Проект своего user_id не имеет — чистится через владельца Дела.
     * См. {@code UserPurgeService}.
     */
    @Modifying
    @Query("DELETE FROM DeloProject dp WHERE dp.delo.id IN (SELECT d.id FROM Delo d WHERE d.user = :user)")
    void deleteAllByUser(@Param("user") User user);
}
