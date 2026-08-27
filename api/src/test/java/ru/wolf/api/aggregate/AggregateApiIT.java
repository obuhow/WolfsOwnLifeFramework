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
package ru.wolf.api.aggregate;

import ru.wolf.api.delo.dto.*;
import ru.wolf.api.timeentry.dto.*;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.lifearea.dto.*;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.project.dto.*;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryController;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ticket 13: aggregates on Project/Delo detail — fact hours by day, same rules as Gantt fact.
 */
class AggregateApiIT extends ApiIntegrationTest {

    @Autowired TimeEntryRepository timeEntryRepository;
    @Autowired DeloRepository deloRepository;
    @Autowired DeloProjectRepository deloProjectRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired LifeAreaRepository lifeAreaRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanup() {
        timeEntryRepository.deleteAll();
        deloProjectRepository.deleteAll();
        deloRepository.deleteAll();
        projectRepository.deleteAll();
        lifeAreaRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> !"admin".equals(u.getUsername()))
                .forEach(userRepository::delete);
        userRepository.findByUsername("admin").ifPresent(u -> {
            u.setTimezone("Europe/Moscow");
            u.setHourAccountingMode("PRIMARY_ONLY");
            u.setDayEnd(LocalTime.MIDNIGHT);
            u.setNightStart(LocalTime.of(23, 0));
            u.setNightEnd(LocalTime.of(7, 0));
            u.setDefaultSleepEnd(LocalTime.of(9, 0));
            userRepository.save(u);
        });
    }

    @Test
    void project_detail_aggregates_primary_only_by_day() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        Long p1 = createProject(authed, areaId, "Primary").id();
        Long p2 = createProject(authed, areaId, "Secondary").id();
        Long deloId = createDelo(authed, "Код", List.of(p1, p2), p1);

        LocalDate d1 = LocalDate.of(2026, 3, 9);
        LocalDate d2 = LocalDate.of(2026, 3, 10);
        putEntry(authed, d1.atTime(10, 0), d1.atTime(12, 0), deloId, TimeEntry.Status.DONE); // 2h
        putEntry(authed, d2.atTime(14, 0), d2.atTime(15, 30), deloId, TimeEntry.Status.DONE); // 1.5h
        putEntry(authed, d1.atTime(16, 0), d1.atTime(17, 0), deloId, TimeEntry.Status.PLANNED); // not fact

        ProjectDetailResponse detail = getProject(authed, p1);
        assertThat(detail.aggregates()).isNotNull();
        assertThat(detail.aggregates().getTotalFactHours()).isEqualByComparingTo("3.50");
        assertThat(detail.aggregates().getHourAccountingMode()).isEqualTo("PRIMARY_ONLY");
        assertThat(detail.aggregates().getByDay()).hasSize(2);
        assertThat(detail.aggregates().getByDay().get(0).getDate()).isEqualTo(d1.toString());
        assertThat(detail.aggregates().getByDay().get(0).getHours()).isEqualByComparingTo("2.00");
        assertThat(detail.aggregates().getByDay().get(1).getDate()).isEqualTo(d2.toString());
        assertThat(detail.aggregates().getByDay().get(1).getHours()).isEqualByComparingTo("1.50");

        // Secondary gets nothing under PRIMARY_ONLY
        ProjectDetailResponse secondary = getProject(authed, p2);
        assertThat(secondary.aggregates().getTotalFactHours()).isEqualByComparingTo("0.00");
        assertThat(secondary.aggregates().getByDay()).isEmpty();
    }

    @Test
    void project_detail_aggregates_all_projects_mode() {
        WebTestClient authed = authedAdminClient();
        userRepository.findByUsername("admin").ifPresent(u -> {
            u.setHourAccountingMode("ALL_PROJECTS");
            userRepository.save(u);
        });

        Long areaId = createLifeArea(authed, "Работа");
        Long p1 = createProject(authed, areaId, "A").id();
        Long p2 = createProject(authed, areaId, "B").id();
        Long deloId = createDelo(authed, "Общее", List.of(p1, p2), p1);

        LocalDate d = LocalDate.of(2026, 3, 9);
        putEntry(authed, d.atTime(14, 0), d.atTime(15, 30), deloId, TimeEntry.Status.DONE);

        ProjectDetailResponse a = getProject(authed, p1);
        ProjectDetailResponse b = getProject(authed, p2);
        assertThat(a.aggregates().getHourAccountingMode()).isEqualTo("ALL_PROJECTS");
        assertThat(a.aggregates().getTotalFactHours()).isEqualByComparingTo("1.50");
        assertThat(b.aggregates().getTotalFactHours()).isEqualByComparingTo("1.50");
    }

    @Test
    void project_detail_excludes_ad_hoc_and_unlinked_delo() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        Long p1 = createProject(authed, areaId, "Проект").id();
        Long deloLinked = createDelo(authed, "Связанное", List.of(p1), p1);
        Long deloOther = createDelo(authed, "Другое", List.of(), null);

        LocalDate d = LocalDate.of(2026, 3, 9);
        putAdHoc(authed, d.atTime(9, 0), d.atTime(11, 0), "Без дела", TimeEntry.Status.DONE);
        putEntry(authed, d.atTime(11, 0), d.atTime(12, 0), deloLinked, TimeEntry.Status.DONE);
        putEntry(authed, d.atTime(13, 0), d.atTime(14, 0), deloOther, TimeEntry.Status.DONE);

        ProjectDetailResponse detail = getProject(authed, p1);
        assertThat(detail.aggregates().getTotalFactHours()).isEqualByComparingTo("1.00");
        assertThat(detail.aggregates().getByDay()).hasSize(1);
    }

    @Test
    void project_detail_respects_day_end_for_day_buckets() {
        WebTestClient authed = authedAdminClient();
        userRepository.findByUsername("admin").ifPresent(u -> {
            u.setDayEnd(LocalTime.of(2, 0));
            userRepository.save(u);
        });

        Long areaId = createLifeArea(authed, "Работа");
        Long p1 = createProject(authed, areaId, "Ночной").id();
        Long deloId = createDelo(authed, "Спринт", List.of(p1), p1);

        // 01:00–02:00 wall on Mar 10 belongs to logical day Mar 9 when dayEnd=02:00
        LocalDateTime start = LocalDateTime.of(2026, 3, 10, 1, 0);
        LocalDateTime end = LocalDateTime.of(2026, 3, 10, 2, 0);
        putEntry(authed, start, end, deloId, TimeEntry.Status.DONE);

        ProjectDetailResponse detail = getProject(authed, p1);
        assertThat(detail.aggregates().getTotalFactHours()).isEqualByComparingTo("1.00");
        assertThat(detail.aggregates().getByDay()).hasSize(1);
        assertThat(detail.aggregates().getByDay().get(0).getDate()).isEqualTo("2026-03-09");
        assertThat(detail.aggregates().getByDay().get(0).getHours()).isEqualByComparingTo("1.00");
    }

    @Test
    void delo_detail_aggregates_done_hours_by_day() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        Long p1 = createProject(authed, areaId, "WOLF").id();
        Long deloId = createDelo(authed, "Релиз", List.of(p1), p1);

        LocalDate d1 = LocalDate.of(2026, 3, 9);
        LocalDate d2 = LocalDate.of(2026, 3, 11);
        putEntry(authed, d1.atTime(10, 0), d1.atTime(11, 0), deloId, TimeEntry.Status.DONE);
        putEntry(authed, d2.atTime(9, 0), d2.atTime(12, 0), deloId, TimeEntry.Status.DONE);
        putEntry(authed, d1.atTime(15, 0), d1.atTime(16, 0), deloId, TimeEntry.Status.PLANNED);

        DeloDetailResponse detail = getDelo(authed, deloId);
        assertThat(detail.aggregates()).isNotNull();
        assertThat(detail.aggregates().getTotalFactHours()).isEqualByComparingTo("4.00");
        assertThat(detail.aggregates().getByDay()).hasSize(2);
        assertThat(detail.aggregates().getByDay().get(0).getDate()).isEqualTo(d1.toString());
        assertThat(detail.aggregates().getByDay().get(0).getHours()).isEqualByComparingTo("1.00");
        assertThat(detail.aggregates().getByDay().get(1).getDate()).isEqualTo(d2.toString());
        assertThat(detail.aggregates().getByDay().get(1).getHours()).isEqualByComparingTo("3.00");
    }

    @Test
    void delo_detail_empty_aggregates_without_done_entries() {
        WebTestClient authed = authedAdminClient();
        Long deloId = createDelo(authed, "Пустое", List.of(), null);

        DeloDetailResponse detail = getDelo(authed, deloId);
        assertThat(detail.aggregates()).isNotNull();
        assertThat(detail.aggregates().getTotalFactHours()).isEqualByComparingTo("0.00");
        assertThat(detail.aggregates().getByDay()).isEmpty();
    }

    @Test
    void project_aggregate_matches_gantt_week_fact_sum() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        Long p1 = createProject(authed, areaId, "Сверка").id();
        Long deloId = createDelo(authed, "Задача", List.of(p1), p1);

        LocalDate monday = LocalDate.of(2026, 3, 9);
        putEntry(authed, monday.atTime(10, 0), monday.atTime(12, 0), deloId, TimeEntry.Status.DONE);
        putEntry(authed, monday.plusDays(2).atTime(9, 0), monday.plusDays(2).atTime(10, 30), deloId, TimeEntry.Status.DONE);

        ProjectDetailResponse detail = getProject(authed, p1);
        assertThat(detail.aggregates().getTotalFactHours()).isEqualByComparingTo("3.50");

        // Gantt week cell should equal sum of by-day for that ISO week
        Map<?, ?> gantt = authed.get()
                .uri("/api/v1/gantt?from=" + monday + "&weeks=1")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        assertThat(gantt).isNotNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projects = (List<Map<String, Object>>) gantt.get("projects");
        Map<String, Object> row = projects.stream()
                .filter(p -> ((Number) p.get("id")).longValue() == p1)
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cells = (List<Map<String, Object>>) row.get("cells");
        BigDecimal fact = new BigDecimal(cells.get(0).get("factHours").toString());
        assertThat(fact).isEqualByComparingTo(detail.aggregates().getTotalFactHours());
    }

    // --- helpers ---

    private ProjectDetailResponse getProject(WebTestClient client, Long id) {
        return client.get()
                .uri("/api/v1/projects/{id}", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProjectDetailResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private DeloDetailResponse getDelo(WebTestClient client, Long id) {
        return client.get()
                .uri("/api/v1/delos/{id}", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloDetailResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private Long createLifeArea(WebTestClient client, String name) {
        return client.post()
                .uri("/api/v1/life-areas")
                .bodyValue(Map.of("name", name, "color", "#3d5a4a"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(LifeAreaResponse.class)
                .returnResult()
                .getResponseBody()
                .id();
    }

    private ProjectResponse createProject(WebTestClient client, Long areaId, String title) {
        Map<String, Object> body = new HashMap<>();
        body.put("lifeAreaId", areaId);
        body.put("title", title);
        return client.post()
                .uri("/api/v1/projects")
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProjectResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private Long createDelo(WebTestClient client, String title, List<Long> projectIds, Long primary) {
        CreateDeloRequest req = new CreateDeloRequest(title, null, Delo.ExecutionMode.SELF, projectIds, primary);
        return client.post()
                .uri("/api/v1/delos")
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloResponse.class)
                .returnResult()
                .getResponseBody()
                .id();
    }

    private void putEntry(
            WebTestClient client,
            LocalDateTime start,
            LocalDateTime end,
            Long deloId,
            TimeEntry.Status status
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("startAt", start.toString());
        body.put("endAt", end.toString());
        body.put("deloId", deloId);
        body.put("status", status.name());
        client.put()
                .uri("/api/v1/time-entries")
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody(TimeEntryResponse.class)
                .returnResult();
    }

    private void putAdHoc(
            WebTestClient client,
            LocalDateTime start,
            LocalDateTime end,
            String text,
            TimeEntry.Status status
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("startAt", start.toString());
        body.put("endAt", end.toString());
        body.put("adHocText", text);
        body.put("status", status.name());
        client.put()
                .uri("/api/v1/time-entries")
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody(TimeEntryResponse.class)
                .returnResult();
    }
}
