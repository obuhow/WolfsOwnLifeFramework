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
package ru.wolf.api.demo.dto;

/**
 * Result of demo registration: the generated login (also the password), and a fresh JWT so the
 * SPA logs the guest straight in. {@code password == username} by design (ticket 07); the guest
 * is shown both so they can log back in later.
 */
public record DemoRegisterResponse(
        String username,
        String password,
        String token,
        String profileDisplayName
) {}
