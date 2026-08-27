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