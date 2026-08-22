package ru.wolf.api.today;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.backlog.BacklogController;
import ru.wolf.api.checklist.ChecklistController;
import ru.wolf.api.support.ApiIntegrationTest;

class TodayChecklistApiIT extends ApiIntegrationTest {
    @Test
    void checklist_done_toggle_and_carry_over() {
        WebTestClient client = authedAdminClient();
        ChecklistController.Response created = client.post().uri("/api/v1/checklist").contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("date", "2026-08-21", "title", "Первый пункт")).exchange().expectStatus().isCreated().expectBody(ChecklistController.Response.class).returnResult().getResponseBody();
        ChecklistController.Response done = client.patch().uri("/api/v1/checklist/{id}", created.id()).contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("done", true)).exchange().expectStatus().isOk().expectBody(ChecklistController.Response.class).returnResult().getResponseBody();
        assertThat(done.done()).isTrue(); assertThat(done.doneAt()).isNotNull();
        ChecklistController.Response undone = client.patch().uri("/api/v1/checklist/{id}", created.id()).contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("done", false)).exchange().expectStatus().isOk().expectBody(ChecklistController.Response.class).returnResult().getResponseBody();
        assertThat(undone.done()).isFalse(); assertThat(undone.doneAt()).isNull();
        client.post().uri("/api/v1/checklist/{id}/carry-over", created.id()).contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("toDate", "2026-08-22")).exchange().expectStatus().isOk();
        client.get().uri("/api/v1/checklist?date=2026-08-21").exchange().expectStatus().isOk().expectBodyList(ChecklistController.Response.class).value(list -> assertThat(list).hasSize(1));
        client.get().uri("/api/v1/checklist?date=2026-08-22").exchange().expectStatus().isOk().expectBodyList(ChecklistController.Response.class).value(list -> assertThat(list).singleElement().satisfies(i -> assertThat(i.done()).isFalse()));
    }

    @Test
    void backlog_fact_is_scoped_to_current_iso_week() {
        WebTestClient client = authedAdminClient();
        ru.wolf.api.delo.DeloController.DeloResponse delo = client.post().uri("/api/v1/delos").contentType(MediaType.APPLICATION_JSON).bodyValue(new ru.wolf.api.delo.DeloController.CreateDeloRequest("Today fact", null, ru.wolf.api.delo.Delo.ExecutionMode.SELF, List.of(), null)).exchange().expectStatus().isOk().expectBody(ru.wolf.api.delo.DeloController.DeloResponse.class).returnResult().getResponseBody();
        client.post().uri("/api/v1/backlog").contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("deloId", delo.getId(), "scope", "WEEK", "period", "2026-W34", "plannedHours", 10)).exchange().expectStatus().isCreated();
        client.put().uri("/api/v1/time-entries").contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("startAt", "2026-08-19T10:00:00", "endAt", "2026-08-19T14:00:00", "deloId", delo.getId(), "status", "DONE")).exchange().expectStatus().isOk();
        client.get().uri("/api/v1/today/backlog?date=2026-08-19").exchange().expectStatus().isOk().expectBody(TodayBacklogController.BacklogResponse.class).value(body -> { assertThat(body.weekId()).isEqualTo("2026-W34"); assertThat(body.totalPlanned()).isEqualByComparingTo("10"); assertThat(body.totalFact()).isEqualByComparingTo("4"); assertThat(body.items()).singleElement().satisfies(i -> { assertThat(i.plannedHours()).isEqualByComparingTo("10"); assertThat(i.factHours()).isEqualByComparingTo("4"); }); });
        client.get().uri("/api/v1/today/backlog?date=2026-08-26").exchange().expectStatus().isOk().expectBody(TodayBacklogController.BacklogResponse.class).value(body -> assertThat(body.items()).isEmpty());
    }
}
