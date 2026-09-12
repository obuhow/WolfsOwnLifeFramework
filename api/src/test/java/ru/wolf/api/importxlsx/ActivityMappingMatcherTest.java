/*
 * WOLF — Wolf's Own Life Framework
 * Copyright (C) 2025 Pavel Obukhov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package ru.wolf.api.importxlsx;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ActivityMappingMatcherTest {

    @Test
    void reuses_mapping_when_legacy_text_has_internal_whitespace_variation() {
        ActivityMapping legacyMapping = ActivityMapping.builder()
                .activityText("Java   Script")
                .build();

        var match = ActivityMappingMatcher.firstMatch(
                List.of(legacyMapping), "  java script  ");

        assertThat(match).containsSame(legacyMapping);
    }
}
