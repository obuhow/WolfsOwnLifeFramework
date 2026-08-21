package ru.wolf.api.focus;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

class FocusReviewApiIT extends ApiIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired UserRepository users;
    @org.springframework.beans.factory.annotation.Autowired DeloRepository delos;
    @org.springframework.beans.factory.annotation.Autowired TimeEntryRepository entries;
    @org.springframework.beans.factory.annotation.Autowired FocusSessionRepository sessions;
    @org.springframework.beans.factory.annotation.Autowired FocusDistractionRepository distractions;

    @BeforeEach void clean() { distractions.deleteAll(); sessions.deleteAll(); entries.deleteAll(); delos.deleteAll(); }

    @Test void preview_is_dry_run_apply_is_idempotent_and_revert_restores_main_delo() {
        User user = users.findByUsername("admin").orElseThrow(); user.setTimeCaptureMode("PRIMARY_FOCUS"); users.save(user);
        Delo main = delos.save(Delo.builder().user(user).title("Main").executionMode(Delo.ExecutionMode.SELF).build());
        Delo target = delos.save(Delo.builder().user(user).title("Review").executionMode(Delo.ExecutionMode.SELF).build());
        WebTestClient client = authedAdminClient();
        Map<?, ?> started = client.post().uri("/api/v1/focus/start").contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("deloId", main.getId(), "startedAt", "2026-08-18T10:00:00")).exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        client.post().uri("/api/v1/focus/{id}/distractions", started.get("id")).contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("deloId", target.getId(), "at", "2026-08-18T10:30:00", "minutes", 30)).exchange().expectStatus().isOk();
        client.post().uri("/api/v1/focus/{id}/stop", started.get("id")).contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("endedAt", "2026-08-18T12:00:00")).exchange().expectStatus().isOk();
        List<TimeEntry> before = entries.findOverlapping(user.getId(), LocalDateTime.of(2026,8,18,10,0), LocalDateTime.of(2026,8,18,12,0)); assertThat(before).hasSize(1);
        Long distractionId = distractions.findAll().get(0).getId();
        String allocation = "{\"allocations\":[{\"distractionId\":" + distractionId + ",\"deloId\":" + target.getId() + ",\"minutes\":30}]}";
        client.post().uri("/api/v1/focus/{id}/review/preview", started.get("id")).contentType(MediaType.APPLICATION_JSON).bodyValue(allocation).exchange().expectStatus().isOk();
        assertThat(entries.findOverlapping(user.getId(), LocalDateTime.of(2026,8,18,10,0), LocalDateTime.of(2026,8,18,12,0))).hasSize(1);
        client.post().uri("/api/v1/focus/{id}/review/apply", started.get("id")).contentType(MediaType.APPLICATION_JSON).bodyValue(allocation).exchange().expectStatus().isOk();
        assertThat(entries.findOverlapping(user.getId(), LocalDateTime.of(2026,8,18,10,0), LocalDateTime.of(2026,8,18,12,0)).stream().map(e -> e.getDelo().getId()).toList()).contains(target.getId());
        long count = entries.count(); client.post().uri("/api/v1/focus/{id}/review/apply", started.get("id")).contentType(MediaType.APPLICATION_JSON).bodyValue(allocation).exchange().expectStatus().isOk(); assertThat(entries.count()).isEqualTo(count);
        client.post().uri("/api/v1/focus/{id}/review/revert", started.get("id")).exchange().expectStatus().isOk();
        assertThat(entries.findOverlapping(user.getId(), LocalDateTime.of(2026,8,18,10,0), LocalDateTime.of(2026,8,18,12,0)).stream().map(e -> e.getDelo().getId()).toList()).contains(main.getId());
        String tooMuch = "{\"allocations\":[{\"distractionId\":" + distractionId + ",\"deloId\":" + target.getId() + ",\"minutes\":150}]}";
        client.post().uri("/api/v1/focus/{id}/review/preview", started.get("id")).contentType(MediaType.APPLICATION_JSON).bodyValue(tooMuch).exchange().expectStatus().isBadRequest();
    }
}
