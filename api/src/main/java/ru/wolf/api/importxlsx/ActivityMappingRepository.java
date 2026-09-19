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
    Optional<ActivityMapping> findByUserAndActivityText(User user, String activityText);

    /**
     * Lookup ignoring letter case (stage-1 of ticket 05: {@code «Сон»}/{@code «сон»} are one
     * activity). Done in Java, not with SQL {@code lower()}: the Alpine/PostgreSQL default collation
     * is {@code C}, whose {@code lower()} does NOT fold Cyrillic, so a SQL {@code lower(a)=lower(b)}
     * comparison would wrongly miss every Russian spelling. Java's {@code toLowerCase()} folds
     * Cyrillic correctly, so we fetch the user's mappings and compare the trimmed, lower-cased text.
     */
    List<ActivityMapping> findByUser(User user);

    default Optional<ActivityMapping> findByUserAndActivityTextIgnoreCase(User user, String text) {
        String key = text == null ? "" : text.trim().toLowerCase();
        return findByUser(user).stream()
                .filter(m -> (m.getActivityText() == null ? "" : m.getActivityText().trim().toLowerCase()).equals(key))
                .findFirst();
    }

    /**
     * Соответствия «текст активности → Дело» ссылаются на Дела профиля;
     * без их удаления очистка упала бы на внешнем ключе. См. {@code UserPurgeService}.
     */
    @Modifying
    @Query("delete from ActivityMapping m where m.user = :user")
    void deleteAllByUser(@Param("user") User user);
}
