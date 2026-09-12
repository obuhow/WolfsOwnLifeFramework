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

import java.util.Collection;
import java.util.Optional;

/** Matches mappings using the same canonical activity key as import preview/apply. */
final class ActivityMappingMatcher {

    private ActivityMappingMatcher() {
    }

    static Optional<ActivityMapping> firstMatch(Collection<ActivityMapping> mappings,
                                                String activityText) {
        String requestedKey = ActivityTextNormalizer.key(activityText);
        return mappings.stream()
                .filter(mapping -> ActivityTextNormalizer.key(mapping.getActivityText()).equals(requestedKey))
                .findFirst();
    }
}
