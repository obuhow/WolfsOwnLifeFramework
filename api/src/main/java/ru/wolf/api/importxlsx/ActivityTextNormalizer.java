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

import java.util.Locale;

/**
 * Canonical comparison rules for activity text imported from a workbook.
 *
 * <p>The display value keeps its original case, while the comparison key is case-insensitive.
 * This deliberately handles only mechanical differences. Similar meanings such as «Спортзал» and
 * «в спортзал» must still be linked by an explicit user-confirmed mapping.</p>
 */
public final class ActivityTextNormalizer {

    private ActivityTextNormalizer() {
    }

    /** Trims leading/trailing whitespace and collapses internal whitespace. */
    public static String normalize(String activityText) {
        if (activityText == null) {
            return "";
        }
        return activityText.trim().replaceAll("\\s+", " ");
    }

    /** Returns the stable, case-insensitive key used for automatic matching/grouping. */
    public static String key(String activityText) {
        return normalize(activityText).toLowerCase(Locale.ROOT);
    }
}
