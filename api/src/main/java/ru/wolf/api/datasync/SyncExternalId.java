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

import jakarta.persistence.*;
import lombok.*;
import ru.wolf.api.user.User;

@Entity
@Table(name = "sync_external_id", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sync_external_id_value", columnNames = {"user_id", "entity_type", "external_id"}),
        @UniqueConstraint(name = "uk_sync_external_id_entity", columnNames = {"user_id", "entity_type", "entity_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SyncExternalId {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "entity_type", nullable = false, length = 80)
    private String entityType;
    @Column(name = "entity_id", nullable = false)
    private Long entityId;
    @Column(name = "external_id", nullable = false, length = 120)
    private String externalId;
}
