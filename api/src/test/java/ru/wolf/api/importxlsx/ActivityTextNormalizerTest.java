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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ActivityTextNormalizerTest {

    @Test
    void automatic_normalization_keeps_display_case_but_folds_whitespace() {
        assertThat(ActivityTextNormalizer.normalize("  Java   ")).isEqualTo("Java");
        assertThat(ActivityTextNormalizer.normalize("в   спортзал")).isEqualTo("в спортзал");
    }

    @Test
    void automatic_key_is_case_insensitive() {
        assertThat(ActivityTextNormalizer.key(" Java "))
                .isEqualTo(ActivityTextNormalizer.key("java"));
        assertThat(ActivityTextNormalizer.key("Проект"))
                .isNotEqualTo(ActivityTextNormalizer.key("Проект игры"));
    }

    @Test
    void null_activity_has_an_empty_key() {
        assertThat(ActivityTextNormalizer.normalize(null)).isEmpty();
        assertThat(ActivityTextNormalizer.key(null)).isEmpty();
    }
}
