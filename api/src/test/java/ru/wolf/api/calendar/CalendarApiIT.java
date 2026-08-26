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
package ru.wolf.api.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.UserRepository;

class CalendarApiIT extends ApiIntegrationTest {
    @Autowired UserRepository users;
    @Autowired DeloRepository delos;
    @Autowired TimeEntryRepository entries;

    @BeforeEach
    void cleanup() {
        entries.deleteAll();
        delos.deleteAll();
    }

    @Test
    void month_returns_entries_by_day_and_six_week_grid() {
        var user = users.findByUsername("admin").orElseThrow();
        var delo = delos.save(Delo.builder().user(user).title("Дело месяца").build());
        entries.save(TimeEntry.builder().user(user).delo(delo).startAt(LocalDateTime.of(2026, 8, 3, 10, 0)).endAt(LocalDateTime.of(2026, 8, 3, 10, 30)).status(TimeEntry.Status.DONE).build());
        entries.save(TimeEntry.builder().user(user).delo(delo).startAt(LocalDateTime.of(2026, 8, 15, 11, 0)).endAt(LocalDateTime.of(2026, 8, 15, 11, 15)).status(TimeEntry.Status.UNKNOWN).build());
        entries.save(TimeEntry.builder().user(user).delo(delo).startAt(LocalDateTime.of(2026, 8, 31, 12, 0)).endAt(LocalDateTime.of(2026, 8, 31, 12, 15)).status(TimeEntry.Status.DONE).build());

        Map body = authedAdminClient().get().uri("/api/v1/calendar/month?month=2026-08")
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        assertThat(body.get("month")).isEqualTo("2026-08");
        assertThat((List<?>) body.get("days")).hasSize(42);
        assertThat(((List<Map>) body.get("days")).stream().mapToInt(day -> ((Number) day.get("totalCount")).intValue()).sum()).isEqualTo(3);
    }

    @Test
    void empty_month_returns_empty_days_and_neighbor_days_are_marked() {
        Map body = authedAdminClient().get().uri("/api/v1/calendar/month?month=2026-02")
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        List<Map> days = (List<Map>) body.get("days");
        assertThat(days).hasSize(35);
        assertThat(days.stream().anyMatch(day -> Boolean.TRUE.equals(day.get("outOfMonth")))).isTrue();
        assertThat(days.stream().mapToInt(day -> ((Number) day.get("totalCount")).intValue()).sum()).isZero();
    }
}
