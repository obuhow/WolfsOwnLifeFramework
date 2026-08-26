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
package ru.wolf.api.focus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

class FocusApiIT extends ApiIntegrationTest {
    @Autowired UserRepository users;
    @Autowired DeloRepository delos;
    @Autowired TimeEntryRepository entries;
    @Autowired FocusSessionRepository sessions;
    @Autowired FocusDistractionRepository distractions;

    @BeforeEach
    void cleanup() {
        distractions.deleteAll();
        sessions.deleteAll();
        entries.deleteAll();
        delos.deleteAll();
        users.findByUsername("admin").ifPresent(user -> {
            user.setTimeCaptureMode("PARALLEL_SLOTS");
            users.save(user);
        });
    }

    @Test
    void parallel_mode_rejects_focus_start() {
        Delo delo = delo("Основное дело");
        WebTestClient client = authedAdminClient();
        client.post().uri("/api/v1/focus/start")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("deloId", delo.getId()))
                .exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("TIME_CAPTURE_MODE_MISMATCH");
    }

    @Test
    void primary_focus_materializes_free_quarter_hour_slots_and_rejects_second_open_session() {
        User admin = users.findByUsername("admin").orElseThrow();
        admin.setTimeCaptureMode("PRIMARY_FOCUS");
        users.save(admin);
        Delo delo = delo("Основное дело");
        WebTestClient client = authedAdminClient();
        Map first = client.post().uri("/api/v1/focus/start")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("deloId", delo.getId(), "startedAt", "2026-08-15T10:07:00"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        client.post().uri("/api/v1/focus/start")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("deloId", delo.getId()))
                .exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("FOCUS_ALREADY_OPEN");

        client.post().uri("/api/v1/focus/{id}/distractions", first.get("id"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("text", "Ревью чужой задачи", "at", "2026-08-15T10:20:00"))
                .exchange().expectStatus().isOk();
        long before = entries.count();
        Map stop = client.post().uri("/api/v1/focus/{id}/stop", first.get("id"))

                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("endedAt", "2026-08-15T11:02:00"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();

        assertThat(((java.util.List<?>) stop.get("materialized"))).hasSize(1);
        assertThat(((java.util.List<?>) stop.get("skippedCells"))).isEmpty();
        assertThat(entries.count()).isEqualTo(before + 1);
        assertThat(distractions.count()).isEqualTo(1);
        assertThat(entries.findAll()).allSatisfy(entry -> {
            assertThat(entry.getStatus()).isEqualTo(TimeEntry.Status.DONE);
            assertThat(entry.getDelo().getId()).isEqualTo(delo.getId());
        });
    }

    @Test
    void occupied_cell_is_skipped_and_short_session_has_no_entries() {
        User admin = users.findByUsername("admin").orElseThrow();
        admin.setTimeCaptureMode("PRIMARY_FOCUS");
        users.save(admin);
        Delo focusDelo = delo("Фокус");
        Delo existingDelo = delo("Уже занято");
        entries.save(TimeEntry.builder().user(admin).delo(existingDelo)
                .startAt(LocalDateTime.of(2026, 8, 15, 10, 15)).endAt(LocalDateTime.of(2026, 8, 15, 10, 30))
                .status(TimeEntry.Status.DONE).build());
        WebTestClient client = authedAdminClient();
        Map first = client.post().uri("/api/v1/focus/start").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("deloId", focusDelo.getId(), "startedAt", "2026-08-15T10:00:00"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        Map stop = client.post().uri("/api/v1/focus/{id}/stop", first.get("id")).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("endedAt", "2026-08-15T11:00:00"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        assertThat(((java.util.List<?>) stop.get("materialized"))).hasSize(2);
        assertThat((java.util.List<String>) stop.get("skippedCells")).contains("2026-08-15T10:15");
        assertThat(entries.findAll().stream().filter(entry -> entry.getDelo().getId().equals(focusDelo.getId()))).hasSize(2);

        Map shortSession = client.post().uri("/api/v1/focus/start").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("deloId", focusDelo.getId(), "startedAt", "2026-08-15T12:00:00"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        long before = entries.count();
        Map shortStop = client.post().uri("/api/v1/focus/{id}/stop", shortSession.get("id")).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("endedAt", "2026-08-15T12:07:00"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        assertThat(entries.count()).isEqualTo(before);
        assertThat(((java.util.List<?>) shortStop.get("materialized"))).isEmpty();
    }

    private Delo delo(String title) {
        return delos.save(Delo.builder().user(users.findByUsername("admin").orElseThrow()).title(title).build());
    }
}

