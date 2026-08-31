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

import jakarta.validation.constraints.NotBlank;

/**
 * Public request to spin up a demo account (release 1.0, ticket 07). Anonymous guest picks one
 * of the three known profiles: {@code worker-class}, {@code wise-freelancer}, {@code free-artist}.
 */
public record DemoRegisterRequest(
        @NotBlank String profileSlug
) {}
