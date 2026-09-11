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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Release 1.4 ticket 04 — class-1 (supporting / «Рутина») classification and normalization. */
class SupportingActivitiesTest {

    @Test
    void normalizes_whitespace_and_case() {
        assertThat(SupportingActivities.normalize("  Еда ")).isEqualTo("Еда");
        assertThat(SupportingActivities.normalize("в   дороге")).isEqualTo("в дороге");
        assertThat(SupportingActivities.normalize(null)).isEmpty();
    }

    @Test
    void recognizes_supporting_titles_case_insensitively() {
        assertThat(SupportingActivities.isSupporting("Сон")).isTrue();
        assertThat(SupportingActivities.isSupporting("еда")).isTrue();
        assertThat(SupportingActivities.isSupporting("В ДОРОГЕ")).isTrue();
        assertThat(SupportingActivities.isSupporting("Обед")).isTrue();
        assertThat(SupportingActivities.isSupporting("Питание")).isTrue();
    }

    @Test
    void does_not_flag_work_delos_as_supporting() {
        assertThat(SupportingActivities.isSupporting("Java")).isFalse();
        assertThat(SupportingActivities.isSupporting("Спортзал")).isFalse();
        assertThat(SupportingActivities.isSupporting("Муз курсы")).isFalse();
    }

    @Test
    void recognizes_sleep_in_any_case() {
        assertThat(SupportingActivities.isSleep("Сон")).isTrue();
        assertThat(SupportingActivities.isSleep("сон")).isTrue();
        assertThat(SupportingActivities.isSleep("СОН")).isTrue();
        assertThat(SupportingActivities.isSleep("Еда")).isFalse();
    }
}
