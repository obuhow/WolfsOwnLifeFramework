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
package ru.wolf.api.demo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Declarative demo profile deserialized from {@code assets/profiles/<slug>.json}.
 * All dates inside are relative offsets from {@code LocalDate.now(zone)} at load time —
 * never absolute dates. See {@code .scratch/wayfinder-releases-05-07/issues/05-demo-profiles-content.md}
 * for the canonical structure ("Рабочий класс" reference draft).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DemoProfile(
        String slug,
        String displayName,
        BigDecimal weeklyHoursNorm,
        String timezone,
        List<Area> areas,
        List<ProfileProject> projects,
        List<Goal> goals,
        DelosSample delosSample,
        List<Routine> routines,
        Integer timeEntriesHistoryDays,
        List<String> ideas,
        List<Note> notes,
        List<Synergy> synergy
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Area(String name, String sphere) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProfileProject(
            String name,
            String area,
            BigDecimal plannedHours,
            Integer startOffsetDays,
            Integer dueOffsetDays,
            String status
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Goal(String name, BigDecimal weeklyBudgetHours, String metric) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DelosSample(List<String> done, List<String> planned, List<String> backlog) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Routine(String name, String rrule, Integer durationMinutes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Note(String title, String body, Boolean fromAgent) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Synergy(String project, String area, String weight) {}
}
