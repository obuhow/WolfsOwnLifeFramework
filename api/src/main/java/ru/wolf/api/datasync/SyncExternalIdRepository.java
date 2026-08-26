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
package ru.wolf.api.datasync;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.wolf.api.user.User;

import java.util.List;
import java.util.Optional;

public interface SyncExternalIdRepository extends JpaRepository<SyncExternalId, Long> {
    Optional<SyncExternalId> findByUserAndEntityTypeAndEntityId(User user, String entityType, Long entityId);
    Optional<SyncExternalId> findByUserAndEntityTypeAndExternalId(User user, String entityType, String externalId);
    List<SyncExternalId> findByUserAndEntityType(User user, String entityType);

    /**
     * Соответствия «внешний id → сущность» указывают на удаляемые сущности профиля
     * без внешнего ключа: после очистки они стали бы битыми ссылками.
     * См. {@code UserPurgeService}.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from SyncExternalId s where s.user = :user")
    void deleteAllByUser(@org.springframework.data.repository.query.Param("user") User user);

}
