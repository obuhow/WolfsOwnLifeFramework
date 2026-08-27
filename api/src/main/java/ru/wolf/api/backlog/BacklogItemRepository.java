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
package ru.wolf.api.backlog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.wolf.api.user.User;
import java.util.List;
import java.util.Optional;

public interface BacklogItemRepository extends JpaRepository<BacklogItem, Long> {
    @Query("select b from BacklogItem b join fetch b.delo where b.user = :user and b.scope = :scope and b.periodId = :period order by b.position asc, b.id asc")
    List<BacklogItem> findPeriod(@Param("user") User user, @Param("scope") BacklogItem.Scope scope, @Param("period") String period);
    Optional<BacklogItem> findByUserAndDeloIdAndScopeAndPeriodId(User user, Long deloId, BacklogItem.Scope scope, String periodId);
    int countByUserAndScope(User user, BacklogItem.Scope scope);
    
    @Query("SELECT b FROM BacklogItem b WHERE b.user = :user AND b.id = :id")
    Optional<BacklogItem> findByUserAndId(@Param("user") User user, @Param("id") Long id);

    void deleteAllByUser(User user);
}
