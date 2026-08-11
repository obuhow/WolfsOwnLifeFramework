package ru.wolf.api.timeentry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class TimeEntryApiIT extends ApiIntegrationTest {

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

    @Autowired
    PasswordEncoder passwordEncoder;

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
            userRepository.save(u);
        });
    }

    @Test
    void today_empty_for_new_user() {
        WebTestClient authed = authedAdminClient();

        TimeEntryController.TodayResponse body = authed.get()
                .uri("/api/v1/time-entries/today")
                .exchange()
                .expectStatus().isOk()
                .expectBody(TimeEntryController.TodayResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.getTimezone()).isEqualTo("Europe/Moscow");
        assertThat(body.getEntries()).isEmpty();
        assertThat(body.getDate()).isEqualTo(LocalDate.now(MOSCOW).toString());
        assertThat(body.getDayStart()).isEqualTo(LocalDate.now(MOSCOW).atStartOfDay().toString());
        assertThat(body.getDayEnd()).isEqualTo(LocalDate.now(MOSCOW).plusDays(1).atStartOfDay().toString());
    }

    @Test
    void create_future_slot_is_planned() {
        WebTestClient authed = authedAdminClient();
        Long deloId = createDelo(authed, "Код");
        LocalDateTime future = nextAlignedSlot(LocalDateTime.now(MOSCOW).plusHours(3));

        TimeEntryController.TimeEntryResponse created = putEntry(authed, future, Map.of("deloId", deloId));

        assertThat(created.getStatus()).isEqualTo(TimeEntry.Status.PLANNED);
        assertThat(created.getDeloId()).isEqualTo(deloId);
        assertThat(created.getDeloTitle()).isEqualTo("Код");
        assertThat(created.getAdHocText()).isNull();
        assertThat(normalize(created.getStartAt())).isEqualTo(normalize(future.toString()));
        assertThat(normalize(created.getEndAt())).isEqualTo(normalize(future.plusMinutes(15).toString()));
    }

    @Test
    void create_past_slot_is_done() {
        WebTestClient authed = authedAdminClient();
        Long deloId = createDelo(authed, "Вчерашний бег");
        LocalDateTime past = nextAlignedSlot(LocalDateTime.now(MOSCOW).minusHours(2));

        TimeEntryController.TimeEntryResponse created = putEntry(authed, past, Map.of("deloId", deloId));

        assertThat(created.getStatus()).isEqualTo(TimeEntry.Status.DONE);
        assertThat(created.getDeloId()).isEqualTo(deloId);
    }

    @Test
    void ad_hoc_without_delo_is_allowed() {
        WebTestClient authed = authedAdminClient();
        LocalDateTime past = nextAlignedSlot(LocalDateTime.now(MOSCOW).minusHours(1));

        TimeEntryController.TimeEntryResponse created = putEntry(authed, past, Map.of("adHocText", "Позвонить маме"));

        assertThat(created.getDeloId()).isNull();
        assertThat(created.getAdHocText()).isEqualTo("Позвонить маме");
        assertThat(created.getStatus()).isEqualTo(TimeEntry.Status.DONE);
        assertThat(deloRepository.count()).isZero();
    }

    @Test
    void uniqueness_one_entry_per_slot() {
        WebTestClient authed = authedAdminClient();
        Long d1 = createDelo(authed, "A");
        Long d2 = createDelo(authed, "B");
        LocalDateTime slot = nextAlignedSlot(LocalDateTime.now(MOSCOW).plusHours(1));

        putEntry(authed, slot, Map.of("deloId", d1));
        TimeEntryController.TimeEntryResponse replaced = putEntry(authed, slot, Map.of("deloId", d2));

        assertThat(replaced.getDeloId()).isEqualTo(d2);
        Long adminId = userRepository.findByUsername("admin").orElseThrow().getId();
        assertThat(timeEntryRepository.findByUserIdAndStartAtBetween(adminId, slot, slot.plusMinutes(1)))
                .hasSize(1);
    }

    @Test
    void clear_entry_is_idempotent() {
        WebTestClient authed = authedAdminClient();
        Long deloId = createDelo(authed, "Очистка");
        LocalDateTime future = nextAlignedSlot(LocalDateTime.now(MOSCOW).plusHours(4));

        putEntry(authed, future, Map.of("deloId", deloId));

        authed.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/time-entries")
                        .queryParam("startAt", future.toString())
                        .build())
                .exchange()
                .expectStatus().isNoContent();

        authed.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/time-entries")
                        .queryParam("startAt", future.toString())
                        .build())
                .exchange()
                .expectStatus().isNoContent();

        TimeEntryController.TodayResponse today = authed.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/time-entries/today")
                        .queryParam("date", future.toLocalDate().toString())
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(TimeEntryController.TodayResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(today.getEntries()).isEmpty();
    }

    @Test
    void reject_unaligned_start() {
        WebTestClient authed = authedAdminClient();
        Long deloId = createDelo(authed, "Кривой");

        Map<String, Object> body = new HashMap<>();
        body.put("startAt", "2026-08-11T10:07:00");
        body.put("deloId", deloId);

        authed.put()
                .uri("/api/v1/time-entries")
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void reject_both_delo_and_adhoc() {
        WebTestClient authed = authedAdminClient();
        Long deloId = createDelo(authed, "Двойное");
        LocalDateTime slot = nextAlignedSlot(LocalDateTime.now(MOSCOW).plusHours(1));

        Map<String, Object> body = new HashMap<>();
        body.put("startAt", slot.toString());
        body.put("deloId", deloId);
        body.put("adHocText", "ещё текст");

        authed.put()
                .uri("/api/v1/time-entries")
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void reject_neither_delo_nor_adhoc() {
        WebTestClient authed = authedAdminClient();
        LocalDateTime slot = nextAlignedSlot(LocalDateTime.now(MOSCOW).plusHours(1));

        authed.put()
                .uri("/api/v1/time-entries")
                .bodyValue(Map.of("startAt", slot.toString()))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void timezone_day_bounds_use_user_timezone() {
        WebTestClient authed = authedAdminClient();
        Long deloId = createDelo(authed, "Граница дня");

        LocalDate day = LocalDate.of(2026, 3, 15);
        LocalDateTime late = day.atTime(23, 45);
        LocalDateTime earlyNext = day.plusDays(1).atTime(0, 0);

        Map<String, Object> body = new HashMap<>();
        body.put("startAt", late.toString());
        body.put("deloId", deloId);
        body.put("status", "PLANNED");
        putEntryRaw(authed, body);

        TimeEntryController.TodayResponse sameDay = authed.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/time-entries/today")
                        .queryParam("date", day.toString())
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(TimeEntryController.TodayResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(sameDay.getEntries()).extracting(e -> normalize(e.getStartAt()))
                .containsExactly(normalize(late.toString()));
        assertThat(sameDay.getDayStart()).isEqualTo(day.atStartOfDay().toString());
        assertThat(sameDay.getDayEnd()).isEqualTo(day.plusDays(1).atStartOfDay().toString());

        TimeEntryController.TodayResponse nextDay = authed.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/time-entries/today")
                        .queryParam("date", day.plusDays(1).toString())
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(TimeEntryController.TodayResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(nextDay.getEntries()).isEmpty();

        List<TimeEntryController.TimeEntryResponse> range = authed.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/time-entries")
                        .queryParam("from", day.atStartOfDay().toString())
                        .queryParam("to", earlyNext.toString())
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(TimeEntryController.TimeEntryResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(range).hasSize(1);
    }

    @Test
    void explicit_status_planned_on_past_is_kept() {
        WebTestClient authed = authedAdminClient();
        Long deloId = createDelo(authed, "План в прошлом");
        LocalDateTime past = nextAlignedSlot(LocalDateTime.now(MOSCOW).minusHours(5));

        Map<String, Object> body = new HashMap<>();
        body.put("startAt", past.toString());
        body.put("deloId", deloId);
        body.put("status", "PLANNED");

        TimeEntryController.TimeEntryResponse created = putEntryRaw(authed, body);
        assertThat(created.getStatus()).isEqualTo(TimeEntry.Status.PLANNED);
    }

    @Test
    void isolation_between_users() {
        WebTestClient admin = authedAdminClient();
        Long adminDelo = createDelo(admin, "Секрет admin");
        LocalDateTime slot = nextAlignedSlot(LocalDateTime.now(MOSCOW).plusHours(2));
        putEntry(admin, slot, Map.of("deloId", adminDelo));

        User user2 = new User();
        user2.setUsername("user2");
        user2.setPasswordHash(passwordEncoder.encode("admin"));
        user2.setTimezone("Europe/Moscow");
        user2.setNightStart(LocalTime.of(23, 0));
        user2.setNightEnd(LocalTime.of(7, 0));
        user2.setHourAccountingMode("PRIMARY_ONLY");
        userRepository.save(user2);

        WebTestClient client2 = authedClient("user2", "admin");
        Long delo2 = createDelo(client2, "Дело user2");
        putEntry(client2, slot, Map.of("deloId", delo2));

        TimeEntryController.TodayResponse adminToday = admin.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/time-entries/today")
                        .queryParam("date", slot.toLocalDate().toString())
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(TimeEntryController.TodayResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(adminToday.getEntries()).hasSize(1);
        assertThat(adminToday.getEntries().get(0).getDeloTitle()).isEqualTo("Секрет admin");

        TimeEntryController.TodayResponse u2Today = client2.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/time-entries/today")
                        .queryParam("date", slot.toLocalDate().toString())
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(TimeEntryController.TodayResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(u2Today.getEntries()).hasSize(1);
        assertThat(u2Today.getEntries().get(0).getDeloTitle()).isEqualTo("Дело user2");

        Map<String, Object> hack = new HashMap<>();
        hack.put("startAt", slot.plusMinutes(15).toString());
        hack.put("deloId", adminDelo);
        client2.put()
                .uri("/api/v1/time-entries")
                .bodyValue(hack)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void unauthenticated_access_rejected() {
        webTestClient.get().uri("/api/v1/time-entries/today").exchange().expectStatus().isForbidden();
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/time-entries")
                        .queryParam("from", "2026-01-01T00:00:00")
                        .queryParam("to", "2026-01-02T00:00:00")
                        .build())
                .exchange().expectStatus().isForbidden();
        webTestClient.put()
                .uri("/api/v1/time-entries")
                .bodyValue(Map.of("startAt", "2026-01-01T10:00:00", "adHocText", "x"))
                .exchange().expectStatus().isForbidden();
        webTestClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/time-entries")
                        .queryParam("startAt", "2026-01-01T10:00:00")
                        .build())
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void foreign_delo_rejected() {
        WebTestClient admin = authedAdminClient();
        Long adminDelo = createDelo(admin, "Чужое");

        User user2 = new User();
        user2.setUsername("user2");
        user2.setPasswordHash(passwordEncoder.encode("admin"));
        user2.setTimezone("Europe/Moscow");
        user2.setNightStart(LocalTime.of(23, 0));
        user2.setNightEnd(LocalTime.of(7, 0));
        user2.setHourAccountingMode("PRIMARY_ONLY");
        userRepository.save(user2);

        WebTestClient client2 = authedClient("user2", "admin");
        LocalDateTime slot = nextAlignedSlot(LocalDateTime.now(MOSCOW).plusHours(1));

        Map<String, Object> body = new HashMap<>();
        body.put("startAt", slot.toString());
        body.put("deloId", adminDelo);

        client2.put()
                .uri("/api/v1/time-entries")
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void past_planned_does_not_auto_flip_without_confirm() {
        WebTestClient authed = authedAdminClient();
        Long deloId = createDelo(authed, "Ждёт подтверждения");
        LocalDateTime past = nextAlignedSlot(LocalDateTime.now(MOSCOW).minusHours(3));

        Map<String, Object> body = new HashMap<>();
        body.put("startAt", past.toString());
        body.put("deloId", deloId);
        body.put("status", "PLANNED");
        putEntryRaw(authed, body);

        // Re-fetch via today — still planned (no background auto-done).
        TimeEntryController.TodayResponse today = authed.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/time-entries/today")
                        .queryParam("date", past.toLocalDate().toString())
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(TimeEntryController.TodayResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(today.getEntries()).hasSize(1);
        assertThat(today.getEntries().get(0).getStatus()).isEqualTo(TimeEntry.Status.PLANNED);
    }

    @Test
    void confirm_single_planned_becomes_done() {
        WebTestClient authed = authedAdminClient();
        Long deloId = createDelo(authed, "Один слот");
        LocalDateTime past = nextAlignedSlot(LocalDateTime.now(MOSCOW).minusHours(2));

        Map<String, Object> body = new HashMap<>();
        body.put("startAt", past.toString());
        body.put("deloId", deloId);
        body.put("status", "PLANNED");
        putEntryRaw(authed, body);

        TimeEntryController.TimeEntryResponse confirmed = authed.post()
                .uri("/api/v1/time-entries/confirm")
                .bodyValue(Map.of("startAt", past.toString()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TimeEntryController.TimeEntryResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(confirmed).isNotNull();
        assertThat(confirmed.getStatus()).isEqualTo(TimeEntry.Status.DONE);
        assertThat(confirmed.getDeloId()).isEqualTo(deloId);

        // Idempotent second confirm
        TimeEntryController.TimeEntryResponse again = authed.post()
                .uri("/api/v1/time-entries/confirm")
                .bodyValue(Map.of("startAt", past.toString()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TimeEntryController.TimeEntryResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(again.getStatus()).isEqualTo(TimeEntry.Status.DONE);
    }

    @Test
    void confirm_missing_entry_is_bad_request() {
        WebTestClient authed = authedAdminClient();
        LocalDateTime past = nextAlignedSlot(LocalDateTime.now(MOSCOW).minusHours(1));

        authed.post()
                .uri("/api/v1/time-entries/confirm")
                .bodyValue(Map.of("startAt", past.toString()))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void confirm_all_only_past_planned_in_range() {
        WebTestClient authed = authedAdminClient();
        Long deloId = createDelo(authed, "Блок");

        LocalDateTime past1 = nextAlignedSlot(LocalDateTime.now(MOSCOW).minusHours(4));
        LocalDateTime past2 = nextAlignedSlot(LocalDateTime.now(MOSCOW).minusHours(2));
        LocalDateTime future = nextAlignedSlot(LocalDateTime.now(MOSCOW).plusHours(3));
        LocalDateTime pastDone = nextAlignedSlot(LocalDateTime.now(MOSCOW).minusHours(1));

        // two past planned
        putEntryRaw(authed, Map.of(
                "startAt", past1.toString(),
                "deloId", deloId,
                "status", "PLANNED"
        ));
        putEntryRaw(authed, Map.of(
                "startAt", past2.toString(),
                "deloId", deloId,
                "status", "PLANNED"
        ));
        // future planned — must stay planned
        putEntryRaw(authed, Map.of(
                "startAt", future.toString(),
                "deloId", deloId,
                "status", "PLANNED"
        ));
        // past already done — not counted
        putEntry(authed, pastDone, Map.of("deloId", deloId));

        LocalDate day = past1.toLocalDate();
        // Range covering the whole day (and possibly next if future spills — still ok)
        LocalDateTime from = day.atStartOfDay();
        LocalDateTime to = day.plusDays(2).atStartOfDay();

        TimeEntryController.ConfirmAllResponse result = authed.post()
                .uri("/api/v1/time-entries/confirm-all")
                .bodyValue(Map.of(
                        "from", from.toString(),
                        "to", to.toString()
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TimeEntryController.ConfirmAllResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(result).isNotNull();
        assertThat(result.getConfirmedCount()).isEqualTo(2);
        assertThat(result.getEntries()).hasSize(2);
        assertThat(result.getEntries()).allMatch(e -> e.getStatus() == TimeEntry.Status.DONE);

        List<TimeEntryController.TimeEntryResponse> range = authed.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/time-entries")
                        .queryParam("from", from.toString())
                        .queryParam("to", to.toString())
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(TimeEntryController.TimeEntryResponse.class)
                .returnResult()
                .getResponseBody();

        Map<String, TimeEntry.Status> byStart = new HashMap<>();
        for (var e : range) {
            byStart.put(normalize(e.getStartAt()), e.getStatus());
        }
        assertThat(byStart.get(normalize(past1.toString()))).isEqualTo(TimeEntry.Status.DONE);
        assertThat(byStart.get(normalize(past2.toString()))).isEqualTo(TimeEntry.Status.DONE);
        assertThat(byStart.get(normalize(future.toString()))).isEqualTo(TimeEntry.Status.PLANNED);
        assertThat(byStart.get(normalize(pastDone.toString()))).isEqualTo(TimeEntry.Status.DONE);
    }

    @Test
    void confirm_all_empty_range_is_zero() {
        WebTestClient authed = authedAdminClient();
        LocalDate day = LocalDate.now(MOSCOW);

        TimeEntryController.ConfirmAllResponse result = authed.post()
                .uri("/api/v1/time-entries/confirm-all")
                .bodyValue(Map.of(
                        "from", day.atStartOfDay().toString(),
                        "to", day.plusDays(1).atStartOfDay().toString()
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TimeEntryController.ConfirmAllResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(result.getConfirmedCount()).isZero();
        assertThat(result.getEntries()).isEmpty();
    }

    @Test
    void confirm_endpoints_unauthenticated_rejected() {
        webTestClient.post()
                .uri("/api/v1/time-entries/confirm")
                .bodyValue(Map.of("startAt", "2026-01-01T10:00:00"))
                .exchange()
                .expectStatus().isForbidden();
        webTestClient.post()
                .uri("/api/v1/time-entries/confirm-all")
                .bodyValue(Map.of(
                        "from", "2026-01-01T00:00:00",
                        "to", "2026-01-02T00:00:00"
                ))
                .exchange()
                .expectStatus().isForbidden();
    }

    private Long createDelo(WebTestClient client, String title) {
        var req = new DeloController.CreateDeloRequest();
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

    private TimeEntryController.TimeEntryResponse putEntry(
            WebTestClient client,
            LocalDateTime startAt,
            Map<String, ?> fields
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("startAt", startAt.toString());
        body.putAll(fields);
        return putEntryRaw(client, body);
    }

    private TimeEntryController.TimeEntryResponse putEntryRaw(
            WebTestClient client,
            Map<String, Object> body
    ) {
        return client.put()
                .uri("/api/v1/time-entries")
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody(TimeEntryController.TimeEntryResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private static LocalDateTime nextAlignedSlot(LocalDateTime base) {
        int minute = base.getMinute();
        int remainder = minute % 15;
        LocalDateTime aligned = base.withSecond(0).withNano(0);
        if (remainder != 0) {
            aligned = aligned.plusMinutes(15 - remainder);
        }
        return aligned.truncatedTo(ChronoUnit.MINUTES);
    }

    private static String normalize(String s) {
        if (s == null) return null;
        if (s.length() == 16) return s + ":00"; // yyyy-MM-ddTHH:mm
        return s;
    }
}
