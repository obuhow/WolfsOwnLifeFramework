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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.wolf.api.user.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeloRepository extends JpaRepository<Delo, Long> {

    @Query("""
            SELECT DISTINCT d FROM Delo d
            LEFT JOIN FETCH d.deloProjects dp
            LEFT JOIN FETCH dp.project
            WHERE d.user = :user
            ORDER BY d.title ASC
            """)
    List<Delo> findByUserOrderByTitleAsc(@Param("user") User user);

    @Query("""
            SELECT DISTINCT d FROM Delo d
            LEFT JOIN FETCH d.deloProjects dp
            LEFT JOIN FETCH dp.project
            WHERE d.user = :user AND d.id = :id
            """)
    Optional<Delo> findByUserAndId(@Param("user") User user, @Param("id") Long id);

    Optional<Delo> findFirstByUserAndTitleIgnoreCaseOrderByIdAsc(User user, String title);

    @Query("""
            SELECT d FROM Delo d
            WHERE d.user = :user AND LOWER(d.title) IN :lowerTitles
            """)
    List<Delo> findByUserAndTitleInIgnoreCase(@Param("user") User user, @Param("lowerTitles") List<String> lowerTitles);

    void deleteAllByUser(User user);
}
