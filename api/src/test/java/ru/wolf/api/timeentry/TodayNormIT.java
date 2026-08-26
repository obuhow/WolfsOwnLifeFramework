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
package ru.wolf.api.timeentry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloController;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

/**
 * Ticket 0.4-07 checklist / bug 0.6-02: daily norm remainder calculation on the
 * Today endpoint. Covers the server half only — the rendering half is
 * {@code TodayView.vue}'s {@code dayNormLabel} (verified in the browser, not here).
 */
class TodayNormIT extends ApiIntegrationTest {

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

    private User admin;

    @BeforeEach
    void cleanup() {
        timeEntryRepository.deleteAll();
        deloProjectRepository.deleteAll();
        deloRepository.deleteAll();
        projectRepository.deleteAll();
        lifeAreaRepository.deleteAll();
        admin = userRepository.findByUsername("admin").orElseThrow();
        admin.setTimezone("Europe/Moscow");
        admin.setDayEnd(LocalTime.of(2, 0));
        admin.setHourAccountingMode("PRIMARY_ONLY");
        admin.setAvailableWeeklyHours(BigDecimal.valueOf(28)); // 4h/day exactly
        admin = userRepository.save(admin);
    }

    @Test
    void normAbsent_whenAvailableWeeklyHoursIsZero() {
        admin.setAvailableWeeklyHours(BigDecimal.ZERO);
        userRepository.save(admin);
        WebTestClient authed = authedAdminClient();

        TimeEntryController.TodayResponse body = todayFor(authed, LocalDate.now());

        assertThat(body.getDayNormMinutes()).isZero();
    }

    @Test
    void normRoundsDownTo15MinuteGrid() {
        // 30h/week / 7 = 4.2857h/day = 257.14min -> floor to 255 (4h15m), not 270 (4h30m).
        admin.setAvailableWeeklyHours(BigDecimal.valueOf(30));
        userRepository.save(admin);
        WebTestClient authed = authedAdminClient();

        TimeEntryController.TodayResponse body = todayFor(authed, LocalDate.now());

        assertThat(body.getDayNormMinutes()).isEqualTo(255);
    }

    @Test
    void remainingPositive_whenFactUnderNorm() {
        // norm = 240 (4h), fact = 45min done -> remaining = 195 (недобор)
        WebTestClient authed = authedAdminClient();
        Long deloId = createDelo(authed, "Код");
        LocalDate day = pastDayFor(admin);
        LocalDateTime start = day.atTime(10, 0);
        putDone(authed, start, start.plusMinutes(45), deloId);

        TimeEntryController.TodayResponse body = todayFor(authed, day);

        assertThat(body.getDayNormMinutes()).isEqualTo(240);
        assertThat(body.getDayFactMinutes()).isEqualTo(45);
        assertThat(body.getRemainingMinutes()).isEqualTo(195);
    }

    @Test
    void remainingNegative_meansOverage_notNegativeRemainder() {
        // norm = 240 (4h), fact = 330 (5h30m) -> remainingMinutes = -90 ("+1ч30м сверх" on FE)
        WebTestClient authed = authedAdminClient();
        Long deloId = createDelo(authed, "Марафон");
        LocalDate day = pastDayFor(admin);
        LocalDateTime start = day.atTime(6, 0);
        putDone(authed, start, start.plusMinutes(330), deloId);

        TimeEntryController.TodayResponse body = todayFor(authed, day);

        assertThat(body.getDayFactMinutes()).isEqualTo(330);
        assertThat(body.getRemainingMinutes()).isEqualTo(-90);
    }

    @Test
    void sleepExcludedFromFact() {
        WebTestClient authed = authedAdminClient();
        Long codeId = createDelo(authed, "Код");
        Long sleepId = createDelo(authed, "Сон");
        LocalDate day = pastDayFor(admin);

        // 1h work + 8h sleep; fact must count only the 1h work.
        LocalDateTime work = day.atTime(10, 0);
        putDone(authed, work, work.plusMinutes(60), codeId);
        LocalDateTime sleepStart = day.atTime(2, 0);
        putDone(authed, sleepStart, sleepStart.plusHours(8), sleepId);

        TimeEntryController.TodayResponse body = todayFor(authed, day);

        assertThat(body.getDayFactMinutes()).isEqualTo(60);
    }

    @Test
    void primaryOnly_doesNotDoubleCountOverlappingParallelSlots() {
        // Simulate two overlapping DONE intervals for the same user (e.g. from a
        // skipOverlapCheck import) — PRIMARY_ONLY must merge, not sum, the overlap.
        WebTestClient authed = authedAdminClient();
        Long deloId = createDelo(authed, "Параллель");
        LocalDate day = pastDayFor(admin);
        LocalDateTime aStart = day.atTime(10, 0);
        LocalDateTime bStart = day.atTime(10, 30);
        Delo delo = deloRepository.findById(deloId).orElseThrow();

        timeEntryRepository.save(TimeEntry.builder()
                .user(admin).delo(delo)
                .startAt(aStart).endAt(aStart.plusMinutes(60))
                .status(TimeEntry.Status.DONE)
                .build());
        timeEntryRepository.save(TimeEntry.builder()
                .user(admin).delo(delo)
                .startAt(bStart).endAt(bStart.plusMinutes(60))
                .status(TimeEntry.Status.DONE)
                .build());
        // Union of [10:00,11:00) and [10:30,11:30) = 90 minutes, not 60+60=120.

        TimeEntryController.TodayResponse body = todayFor(authed, day);

        assertThat(body.getDayFactMinutes()).isEqualTo(90);
    }

    @Test
    void logicalDay_dayEnd0200_postMidnightBelongsToPreviousLogicalDay() {
        // dayEnd=02:00: work at 00:30-01:15 on calendar date D+1 belongs to logical day D.
        WebTestClient authed = authedAdminClient();
        Long deloId = createDelo(authed, "Ночная работа");
        LocalDate logicalDay = LocalDate.of(2026, 3, 15);
        LocalDateTime postMidnight = logicalDay.plusDays(1).atTime(0, 30);
        putDone(authed, postMidnight, postMidnight.plusMinutes(45), deloId);

        TimeEntryController.TodayResponse sameLogicalDay = todayFor(authed, logicalDay);
        TimeEntryController.TodayResponse nextLogicalDay = todayFor(authed, logicalDay.plusDays(1));

        assertThat(sameLogicalDay.getDayFactMinutes()).isEqualTo(45);
        assertThat(nextLogicalDay.getDayFactMinutes()).isZero();
    }

    // --- helpers ---

    /** A day far enough in the past that any slot inside it resolves to DONE by default. */
    private LocalDate pastDayFor(User user) {
        return LocalDate.now(java.time.ZoneId.of(user.getTimezone())).minusDays(3);
    }

    private TimeEntryController.TodayResponse todayFor(WebTestClient client, LocalDate date) {
        return client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/time-entries/today")
                        .queryParam("date", date.toString())
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(TimeEntryController.TodayResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private void putDone(WebTestClient client, LocalDateTime start, LocalDateTime end, Long deloId) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("startAt", start.toString());
        body.put("endAt", end.toString());
        body.put("deloId", deloId);
        body.put("status", "DONE");
        client.put()
                .uri("/api/v1/time-entries")
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk();
    }

    private Long createDelo(WebTestClient client, String title) {
        DeloController.CreateDeloRequest req = new DeloController.CreateDeloRequest();
        req.setTitle(title);
        req.setExecutionMode(Delo.ExecutionMode.SELF);
        DeloController.DeloResponse created = client.post()
                .uri("/api/v1/delos")
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloController.DeloResponse.class)
                .returnResult()
                .getResponseBody();
        return created.getId();
    }
}
