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
package ru.wolf.api.idea;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.wolf.api.user.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface IdeaRepository extends JpaRepository<Idea, Long> {

    @Query("""
            SELECT i FROM Idea i
            LEFT JOIN FETCH i.promotedProject
            WHERE i.user = :user
              AND (:category IS NULL OR i.category = :category)
              AND (:status IS NULL OR i.status = :status)
            ORDER BY i.updatedAt DESC, i.id DESC
            """)
    List<Idea> findForUser(
            @Param("user") User user,
            @Param("category") Idea.Category category,
            @Param("status") Idea.Status status
    );

    @Query("SELECT i FROM Idea i LEFT JOIN FETCH i.promotedProject WHERE i.user = :user AND i.id = :id")
    Optional<Idea> findByUserAndId(@Param("user") User user, @Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Idea i WHERE i.user = :user AND i.id = :id")
    Optional<Idea> findByUserAndIdForUpdate(@Param("user") User user, @Param("id") Long id);

    void deleteAllByUser(User user);
}
