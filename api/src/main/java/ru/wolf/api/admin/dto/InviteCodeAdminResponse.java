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
import java.util.UUID;

public record InviteCodeAdminResponse(
        UUID id,
        String code,
        String createdByUsername,
        Integer maxUses,
        Integer usedCount,
        Instant expiresAt,
        Instant revokedAt,
        String note,
        Instant createdAt
) {

    public static InviteCodeAdminResponse from(ru.wolf.api.invite.InviteCode code) {
        return new InviteCodeAdminResponse(
                code.getId(),
                code.getCode(),
                code.getCreatedBy().getUsername(),
                code.getMaxUses(),
                code.getUsedCount(),
                code.getExpiresAt(),
                code.getRevokedAt(),
                code.getNote(),
                code.getCreatedAt()
        );
    }
}
