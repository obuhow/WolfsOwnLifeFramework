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
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.importxlsx.dto;

/**
 * Release 1.4 ticket 05 (stage 2): the mapping a confirmed semantic merge produced.
 *
 * @param activityText the canonical (stage-1) text the merge was stored under
 * @param deloId       id of the Дело the activity now resolves to
 * @param deloTitle    title of that Дело
 */
public record NormalizeResponse(String activityText, Long deloId, String deloTitle) {
}
