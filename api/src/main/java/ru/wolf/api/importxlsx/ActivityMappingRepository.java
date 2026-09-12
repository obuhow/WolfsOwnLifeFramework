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
package ru.wolf.api.importxlsx;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.wolf.api.user.User;

import java.util.List;
import java.util.Optional;

public interface ActivityMappingRepository extends JpaRepository<ActivityMapping, Long> {
    /** Loads mappings in deterministic order for the shared Java-side normalization key. */
    List<ActivityMapping> findByUserOrderByIdAsc(User user);

    /** Fast path for the common trim/case-only variant. */
    @Query("""
            select m from ActivityMapping m
            where m.user = :user
              and lower(trim(m.activityText)) = lower(trim(:activityText))
            order by m.id asc
            """)
    Optional<ActivityMapping> findByUserAndTrimmedActivityText(@Param("user") User user,
                                                                 @Param("activityText") String activityText);

    /** Finds a mapping using exactly the same key as preview/apply normalization. */
    default Optional<ActivityMapping> findByUserAndNormalizedActivityText(User user, String activityText) {
        return findByUserAndTrimmedActivityText(user, activityText)
                .or(() -> ActivityMappingMatcher.firstMatch(findByUserOrderByIdAsc(user), activityText));
    }

    /** Kept for callers that need the exact legacy lookup semantics. */
    Optional<ActivityMapping> findByUserAndActivityText(User user, String activityText);

    /**
     * Соответствия «текст активности → Дело» ссылаются на Дела профиля;
     * без их удаления очистка упала бы на внешнем ключе. См. {@code UserPurgeService}.
     */
    @Modifying
    @Query("delete from ActivityMapping m where m.user = :user")
    void deleteAllByUser(@Param("user") User user);
}
