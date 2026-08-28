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
 * GNU Affero General License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.max;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * Permanent Max ↔ WOLF account link (release 0.7, ticket 04). Mirrors
 * {@link ru.wolf.api.telegram.TelegramLink}: {@code chatId} is the Max dialog
 * id (stored as a string — Max returns it as an int64), {@code userId} is the
 * WOLF {@code "user".id}. A separate table, the {@link ru.wolf.api.user.User}
 * entity is not extended.
 */
@Entity
@Table(name = "max_link")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaxLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false, unique = true)
    private String chatId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
