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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import ru.wolf.api.timeentry.TimeEntry;

class ScheduleEntryGrouperTest {

    @Test
    void three_contiguous_cells_of_same_activity_become_one_45_minute_interval() {
        List<ScheduleEntryGrouper.Candidate> cells = List.of(
                candidate("09:00", "Java", 7L),
                candidate("09:15", "Java", 7L),
                candidate("09:30", "Java", 7L));

        List<ScheduleEntryGrouper.Group> groups = ScheduleEntryGrouper.group(cells);

        assertThat(groups).singleElement().satisfies(group -> {
            assertThat(group.startAt()).isEqualTo(cells.get(0).cell().startAt());
            assertThat(group.endAt()).isEqualTo(LocalDate.of(2026, 6, 1).atTime(9, 45));
            assertThat(group.cells()).hasSize(3);
        });
    }

    @Test
    void gap_activity_change_and_day_boundary_start_new_groups() {
        List<ScheduleEntryGrouper.Candidate> cells = List.of(
                candidate("23:30", "Java", 7L),
                candidate("23:45", "Java", 7L),
                candidate("00:00", "Java", 7L, LocalDate.of(2026, 6, 2)),
                candidate("00:15", "Gym", 8L, LocalDate.of(2026, 6, 2)),
                candidate("00:45", "Gym", 8L, LocalDate.of(2026, 6, 2)));

        List<ScheduleEntryGrouper.Group> groups = ScheduleEntryGrouper.group(cells);

        assertThat(groups).extracting(ScheduleEntryGrouper.Group::startAt)
                .containsExactly(
                        LocalDate.of(2026, 6, 1).atTime(23, 30),
                        LocalDate.of(2026, 6, 2).atStartOfDay(),
                        LocalDate.of(2026, 6, 2).atTime(0, 15),
                        LocalDate.of(2026, 6, 2).atTime(0, 45));
        assertThat(groups.get(0).endAt()).isEqualTo(LocalDate.of(2026, 6, 2).atStartOfDay());
    }

    @Test
    void duplicate_source_slots_are_rejected_before_grouping() {
        List<ScheduleEntryGrouper.Candidate> cells = List.of(
                candidate("09:00", "Java", 7L),
                candidate("09:00", "Gym", 8L));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ScheduleEntryGrouper.group(cells))
                .withMessageContaining("несколько активностей");
    }

    private static ScheduleEntryGrouper.Candidate candidate(String time, String activity, Long deloId) {
        return candidate(time, activity, deloId, LocalDate.of(2026, 6, 1));
    }

    private static ScheduleEntryGrouper.Candidate candidate(String time, String activity, Long deloId,
                                                              LocalDate date) {
        return new ScheduleEntryGrouper.Candidate(
                new XlsxScheduleGridParser.ScheduleCell(date, LocalTime.parse(time), activity, "week"),
                ActivityTextNormalizer.key(activity),
                deloId,
                deloId == null ? TimeEntry.Status.UNKNOWN : TimeEntry.Status.DONE);
    }
}
