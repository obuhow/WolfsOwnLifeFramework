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
package ru.wolf.api.recurrence;

import ru.wolf.api.delo.dto.*;
import ru.wolf.api.timeentry.dto.*;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryController;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.UserRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class RecurrenceApiIT extends ApiIntegrationTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    @Autowired
    TimeEntryRepository timeEntryRepository;

    @Autowired
    DeloRepository deloRepository;

    @Autowired
    DeloProjectRepository deloProjectRepository;

    @Autowired
    ProjectRepository projectRepository;

    @Autowired
    LifeAreaRepository lifeAreaRepository;

    @Autowired
    UserRepository userRepository;

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
            u.setDayEnd(LocalTime.MIDNIGHT);
            userRepository.save(u);
        });
    }

    @Test
    void apply_creates_future_planned_entries_only_within_horizon() {
        WebTestClient authed = authedAdminClient();
        Long deloId = createDelo(authed, "Утренний бег");

        ApplyResult applied = applyRecurrence(authed, deloId, Map.of(
                "weekdays", List.of("WEDNESDAY"),
                "windowStart", "09:00",
                "windowEnd", "10:00",
                "horizonWeeks", 4
        ));

        LocalDate today = LocalDate.now(MOSCOW);
        LocalDate horizonEnd = today.plusWeeks(4);
        List<LocalDate> expectedDays = expectedFutureDays(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), today, horizonEnd);
        assertThat(expectedDays).isNotEmpty();

        assertThat(applied.created()).isEqualTo(expectedDays.size());

        LocalDateTime rangeFrom = today.atStartOfDay();
        LocalDateTime rangeTo = horizonEnd.atStartOfDay();
        List<TimeEntryResponse> inHorizon = listRange(authed, rangeFrom, rangeTo).stream()
                .filter(e -> deloId.equals(e.getDeloId()))
                .toList();

        assertThat(inHorizon).hasSize(expectedDays.size());
        assertThat(inHorizon).allMatch(e -> e.getStatus() == TimeEntry.Status.PLANNED);
        assertThat(inHorizon).allMatch(e -> normalize(e.getStartAt()).endsWith("T09:00:00"));
        assertThat(inHorizon).allMatch(e -> normalize(e.getEndAt()).endsWith("T10:00:00"));

        List<LocalDate> createdDays = inHorizon.stream()
                .map(e -> LocalDateTime.parse(normalize(e.getStartAt())).toLocalDate())
                .sorted()
                .toList();
        assertThat(createdDays).containsExactlyElementsOf(expectedDays);

        List<TimeEntryResponse> beyond = listRange(
                authed,
                horizonEnd.atStartOfDay(),
                horizonEnd.plusWeeks(2).atStartOfDay()
        ).stream().filter(e -> deloId.equals(e.getDeloId())).toList();
        assertThat(beyond).isEmpty();
    }

    @Test
    void apply_does_not_rewrite_historical_done() {
        WebTestClient authed = authedAdminClient();
        Long deloId = createDelo(authed, "Утренний бег");

        LocalDate lastWednesday = LocalDate.now(MOSCOW).with(java.time.temporal.TemporalAdjusters.previous(DayOfWeek.WEDNESDAY));
        LocalDateTime doneStart = lastWednesday.atTime(9, 0);
        LocalDateTime doneEnd = lastWednesday.atTime(10, 0);
        TimeEntryResponse historical = putEntry(authed, Map.of(
                "startAt", doneStart.toString(),
                "endAt", doneEnd.toString(),
                "deloId", deloId,
                "status", "DONE"
        ));
        assertThat(historical.getStatus()).isEqualTo(TimeEntry.Status.DONE);
        Long historicalId = historical.getId();

        LocalDate nextWednesday = LocalDate.now(MOSCOW).with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.WEDNESDAY));
        LocalDateTime futureDoneStart = nextWednesday.atTime(9, 0);
        TimeEntryResponse futureDone = putEntry(authed, Map.of(
                "startAt", futureDoneStart.toString(),
                "endAt", nextWednesday.atTime(10, 0).toString(),
                "deloId", deloId,
                "status", "DONE"
        ));

        ApplyResult applied = applyRecurrence(authed, deloId, Map.of(
                "weekdays", List.of("WEDNESDAY"),
                "windowStart", "09:00",
                "windowEnd", "10:00",
                "horizonWeeks", 4
        ));

        LocalDate today = LocalDate.now(MOSCOW);
        LocalDate horizonEnd = today.plusWeeks(4);
        List<LocalDate> expectedFuture = expectedFutureDays(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), today, horizonEnd);
        int expectedCreated = (int) expectedFuture.stream().filter(d -> !d.equals(nextWednesday)).count();
        assertThat(applied.created()).isEqualTo(expectedCreated);

        TimeEntryResponse stillDone = listRange(authed, doneStart, doneEnd.plusMinutes(1)).stream()
                .filter(e -> historicalId.equals(e.id()))
                .findFirst()
                .orElseThrow();
        assertThat(stillDone.getStatus()).isEqualTo(TimeEntry.Status.DONE);
        assertThat(normalize(stillDone.getStartAt())).isEqualTo(normalize(doneStart.toString()));
        assertThat(normalize(stillDone.getEndAt())).isEqualTo(normalize(doneEnd.toString()));
        assertThat(stillDone.getDeloId()).isEqualTo(deloId);

        TimeEntryResponse futureStillDone = listRange(authed, futureDoneStart, futureDoneStart.plusHours(1)).stream()
                .filter(e -> futureDone.getId().equals(e.id()))
                .findFirst()
                .orElseThrow();
        assertThat(futureStillDone.getStatus()).isEqualTo(TimeEntry.Status.DONE);
        assertThat(futureStillDone.getDeloId()).isEqualTo(deloId);
    }

    @Test
    void apply_stores_rule_on_delo() {
        WebTestClient authed = authedAdminClient();
        Long deloId = createDelo(authed, "Йога");

        applyRecurrence(authed, deloId, Map.of(
                "weekdays", List.of("MONDAY", "FRIDAY"),
                "windowStart", "18:00",
                "windowEnd", "19:00",
                "horizonWeeks", 2
        ));

        DeloDetailResponse detail = authed.get()
                .uri("/api/v1/delos/{id}", deloId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloDetailResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(detail).isNotNull();
        assertThat(detail.recurrenceWeekdays()).containsExactly(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
        assertThat(detail.recurrenceWindowStart()).isEqualTo(LocalTime.of(18, 0));
        assertThat(detail.recurrenceWindowEnd()).isEqualTo(LocalTime.of(19, 0));
    }

    @Test
    void apply_uses_different_windows_per_weekday() {
        WebTestClient authed = authedAdminClient();
        Long deloId = createDelo(authed, "Спортзал");

        ApplyResult applied = applyRecurrence(authed, deloId, Map.of(
                "slots", List.of(
                        Map.of("weekday", "TUESDAY", "windowStart", "20:00", "windowEnd", "21:30"),
                        Map.of("weekday", "SATURDAY", "windowStart", "10:00", "windowEnd", "11:00")
                ),
                "horizonWeeks", 4
        ));

        LocalDate today = LocalDate.now(MOSCOW);
        LocalDate horizonEnd = today.plusWeeks(4);
        List<LocalDate> tuesdays = expectedFutureDays(DayOfWeek.TUESDAY, LocalTime.of(20, 0), today, horizonEnd);
        List<LocalDate> saturdays = expectedFutureDays(DayOfWeek.SATURDAY, LocalTime.of(10, 0), today, horizonEnd);
        assertThat(tuesdays).isNotEmpty();
        assertThat(saturdays).isNotEmpty();
        assertThat(applied.created()).isEqualTo(tuesdays.size() + saturdays.size());

        List<TimeEntryResponse> inHorizon = listRange(authed, today.atStartOfDay(), horizonEnd.atStartOfDay())
                .stream()
                .filter(e -> deloId.equals(e.getDeloId()))
                .toList();

        List<TimeEntryResponse> tueEntries = inHorizon.stream()
                .filter(e -> LocalDateTime.parse(normalize(e.getStartAt())).getDayOfWeek() == DayOfWeek.TUESDAY)
                .toList();
        List<TimeEntryResponse> satEntries = inHorizon.stream()
                .filter(e -> LocalDateTime.parse(normalize(e.getStartAt())).getDayOfWeek() == DayOfWeek.SATURDAY)
                .toList();

        assertThat(tueEntries).hasSize(tuesdays.size());
        assertThat(tueEntries).allMatch(e -> normalize(e.getStartAt()).endsWith("T20:00:00"));
        assertThat(tueEntries).allMatch(e -> normalize(e.getEndAt()).endsWith("T21:30:00"));
        assertThat(tueEntries).allMatch(e -> e.getStatus() == TimeEntry.Status.PLANNED);

        assertThat(satEntries).hasSize(saturdays.size());
        assertThat(satEntries).allMatch(e -> normalize(e.getStartAt()).endsWith("T10:00:00"));
        assertThat(satEntries).allMatch(e -> normalize(e.getEndAt()).endsWith("T11:00:00"));

        DeloDetailResponse detail = authed.get()
                .uri("/api/v1/delos/{id}", deloId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloDetailResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(detail).isNotNull();
        assertThat(detail.recurrenceSlots()).hasSize(2);
        assertThat(detail.recurrenceSlots())
                .extracting(ru.wolf.api.delo.dto.RecurrenceSlotDto::weekday)
                .containsExactly(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY);
        assertThat(detail.recurrenceSlots().get(0).windowStart()).isEqualTo(LocalTime.of(20, 0));
        assertThat(detail.recurrenceSlots().get(0).windowEnd()).isEqualTo(LocalTime.of(21, 30));
        assertThat(detail.recurrenceSlots().get(1).windowStart()).isEqualTo(LocalTime.of(10, 0));
        assertThat(detail.recurrenceSlots().get(1).windowEnd()).isEqualTo(LocalTime.of(11, 0));
    }

    @Test
    void unauthenticated_apply_rejected() {
        webTestClient.post()
                .uri("/api/v1/delos/1/apply-recurrence")
                .bodyValue(Map.of("weekdays", List.of("MONDAY"), "horizonWeeks", 4))
                .exchange()
                .expectStatus().isForbidden();
    }

    private ApplyResult applyRecurrence(WebTestClient client, Long deloId, Map<String, ?> request) {
        ApplyRecurrenceResponse applied = client.post()
                .uri("/api/v1/delos/{id}/apply-recurrence", deloId)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ApplyRecurrenceResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(applied).isNotNull();
        return new ApplyResult(applied.created());
    }

    private List<TimeEntryResponse> listRange(
            WebTestClient client,
            LocalDateTime from,
            LocalDateTime to
    ) {
        List<TimeEntryResponse> all = new ArrayList<>();
        LocalDateTime cursor = from;
        while (cursor.isBefore(to)) {
            LocalDateTime next = cursor.plusDays(7);
            if (next.isAfter(to)) {
                next = to;
            }
            LocalDateTime chunkFrom = cursor;
            LocalDateTime chunkTo = next;
            List<TimeEntryResponse> chunk = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/time-entries")
                            .queryParam("from", chunkFrom.toString())
                            .queryParam("to", chunkTo.toString())
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(TimeEntryResponse.class)
                    .returnResult()
                    .getResponseBody();
            if (chunk != null) {
                all.addAll(chunk);
            }
            cursor = next;
        }
        return all;
    }

    private TimeEntryResponse putEntry(WebTestClient client, Map<String, Object> body) {
        return client.put()
                .uri("/api/v1/time-entries")
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody(TimeEntryResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private Long createDelo(WebTestClient client, String title) {
        var req = new CreateDeloRequest(title, null, Delo.ExecutionMode.SELF, null, null);
        DeloResponse created = client.post()
                .uri("/api/v1/delos")
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloResponse.class)
                .returnResult()
                .getResponseBody();
        return created.id();
    }

    private static List<LocalDate> expectedFutureDays(
            DayOfWeek weekday,
            LocalTime windowStart,
            LocalDate fromInclusive,
            LocalDate toExclusive
    ) {
        LocalDateTime now = LocalDateTime.now(MOSCOW).truncatedTo(ChronoUnit.SECONDS);
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = fromInclusive; d.isBefore(toExclusive); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != weekday) {
                continue;
            }
            LocalDateTime start = d.atTime(windowStart);
            if (!start.isBefore(now)) {
                days.add(d);
            }
        }
        return days;
    }

    private static String normalize(String s) {
        if (s == null) {
            return null;
        }
        if (s.length() == 16) {
            return s + ":00";
        }
        return s;
    }

    private record ApplyResult(int created) {}
}
