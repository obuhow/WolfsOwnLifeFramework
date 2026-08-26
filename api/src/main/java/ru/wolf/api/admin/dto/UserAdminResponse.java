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
 * along with this program. If not see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.admin.dto;

import java.time.Instant;

public record UserAdminResponse(
        Long id,
        String username,
        String email,
        String role,
        String status,
        String accountType,
        Instant createdAt,
        Instant lastLoginAt,
        long projectCount,
        long deloCount,
        long timeEntryCount
) {

    public static UserAdminResponse from(ru.wolf.api.user.User user) {
        return new UserAdminResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getAccountType(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                0,
                0,
                0
        );
    }
}
