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
 * but WITHOUT ANY WARRANTY; without even implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.importxlsx;

import java.util.List;
import java.util.Locale;

/**
 * Release 1.4 ticket 04 (decision И-A / И-B): the owner's schedule splits activities into three
 * classes — (1) supporting activities (Сон, Еда, В дороге, Питание, Обед…), colloquially «Рутина»;
 * (2) work Delos; (3) unknown / empty. Class 1 still becomes a {@code Delo} in WOLF (it is linked
 * from Записи времени like any other), only marked {@code supporting=true} so the UI and the
 * planning agent can group it apart from unit-of-work Delos.
 *
 * <p>«Сон» (any case/spacing variant) is a special case of class 1: it reuses the existing Дело of
 * the «Ночные часы» mechanism (decision Т-1) instead of creating a duplicate.
 */
public final class SupportingActivities {

    /** Canonical title of the sleep Дело reused from the «Ночные часы» mechanism (Т-1). */
    public static final String SLEEP_DELO_TITLE = "Сон";

    /** Titles that belong to class 1 (supporting / «Рутина»), case-insensitive on compare. */
    private static final List<String> SUPPORTING_TITLES = List.of(
            "Сон",
            "Еда",
            "В дороге",
            "Питание",
            "Обед",
            "Завтрак",
            "Ужин",
            "Полдник",
            "Душ",
            "Гигиена",
            "Отдых",
            "Прогулка"
    );

    private SupportingActivities() {
    }

    /** Trims and collapses internal whitespace so «в  спортзал» and «В СПОРТЗАЛ» compare cleanly. */
    public static String normalize(String activity) {
        if (activity == null) {
            return "";
        }
        return activity.trim().replaceAll("\\s+", " ");
    }

    /** True when the (normalized) activity text is the sleep Дело (Т-1). */
    public static boolean isSleep(String activity) {
        return normalize(activity).equalsIgnoreCase(SLEEP_DELO_TITLE);
    }

    /**
     * True when the (normalized) activity is a class-1 supporting activity. Sleep is included,
     * because it is a supporting activity too — but callers that create a Дело must handle sleep
     * via {@link #isSleep(String)} first to avoid duplicating the «Ночные часы» Дело.
     */
    public static boolean isSupporting(String activity) {
        String norm = normalize(activity).toLowerCase(Locale.ROOT);
        return SUPPORTING_TITLES.stream().anyMatch(t -> t.toLowerCase(Locale.ROOT).equals(norm));
    }
}
