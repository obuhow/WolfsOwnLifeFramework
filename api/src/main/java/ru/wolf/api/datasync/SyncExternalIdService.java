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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.user.User;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SyncExternalIdService {
    private final SyncExternalIdRepository repository;

    @Transactional
    public String externalId(User user, String entityType, Long entityId) {
        return repository.findByUserAndEntityTypeAndEntityId(user, entityType, entityId)
                .map(SyncExternalId::getExternalId)
                .orElseGet(() -> repository.save(SyncExternalId.builder()
                        .user(user)
                        .entityType(entityType)
                        .entityId(entityId)
                        .externalId(entityType + "-" + UUID.randomUUID())
                        .build()).getExternalId());
    }
}
