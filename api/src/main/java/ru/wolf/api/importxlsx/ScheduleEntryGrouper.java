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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import ru.wolf.api.timeentry.TimeEntry;

/** Groups parsed 15-minute cells into continuous time-entry intervals. */
final class ScheduleEntryGrouper {

    private ScheduleEntryGrouper() {
    }

    /** A parsed cell with the resolved import identity used by the grouping contract. */
    record Candidate(XlsxScheduleGridParser.ScheduleCell cell,
                     String activityKey,
                     Long deloId,
                     TimeEntry.Status status) {
    }

    /** One interval that can be materialized as a single {@code TimeEntry}. */
    record Group(List<Candidate> cells) {
        Group {
            cells = List.copyOf(cells);
        }

        LocalDateTime startAt() {
            return cells.get(0).cell().startAt();
        }

        LocalDateTime endAt() {
            return cells.get(cells.size() - 1).cell().endAt();
        }

        Candidate first() {
            return cells.get(0);
        }
    }

    static List<Group> group(List<Candidate> candidates) {
        ensureUniqueStarts(candidates);
        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparing(candidate -> candidate.cell().startAt()));

        List<Group> groups = new ArrayList<>();
        List<Candidate> current = new ArrayList<>();
        for (Candidate candidate : sorted) {
            if (!current.isEmpty() && canAppend(current.get(current.size() - 1), candidate)) {
                current.add(candidate);
                continue;
            }
            if (!current.isEmpty()) {
                groups.add(new Group(current));
            }
            current = new ArrayList<>();
            current.add(candidate);
        }
        if (!current.isEmpty()) {
            groups.add(new Group(current));
        }
        return List.copyOf(groups);
    }

    static void ensureUniqueStarts(List<Candidate> candidates) {
        HashSet<LocalDateTime> starts = new HashSet<>();
        for (Candidate candidate : candidates) {
            if (!starts.add(candidate.cell().startAt())) {
                throw new IllegalArgumentException(
                        "Файл содержит несколько активностей в слоте " + candidate.cell().startAt());
            }
        }
    }

    private static boolean canAppend(Candidate previous, Candidate next) {
        return previous.cell().date().equals(next.cell().date())
                && previous.cell().endAt().equals(next.cell().startAt())
                && Objects.equals(previous.activityKey(), next.activityKey())
                && Objects.equals(previous.deloId(), next.deloId())
                && previous.status() == next.status();
    }
}
