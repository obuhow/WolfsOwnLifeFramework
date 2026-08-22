package ru.wolf.api.datasync;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.support.ApiIntegrationTest;

/**
 * Ticket 21-followup: data-sync endpoints must require a valid JWT again
 * (authorization was temporarily loosened to permitAll during the 0.21 acceptance run).
 */
class DataSyncAuthApiIT extends ApiIntegrationTest {

    @Test
    void export_without_token_is_rejected() {
        webTestClient.get()
                .uri("/api/v1/data-sync/export")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isIn(401, 403));
    }

    @Test
    void manifest_without_token_is_rejected() {
        webTestClient.get()
                .uri("/api/v1/data-sync/manifest")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isIn(401, 403));
    }

    @Test
    void import_preview_without_token_is_rejected() {
        webTestClient.get()
                .uri("/api/v1/data-sync/import/1/preview")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isIn(401, 403));
    }

    @Test
    void export_with_valid_token_succeeds() {
        WebTestClient authed = authedAdminClient();

        byte[] body = authed.get()
                .uri("/api/v1/data-sync/export")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .expectBody(byte[].class)
                .returnResult()
                .getResponseBody();

        // A real XLSX (ZIP) workbook starts with the "PK" magic bytes.
        assertThat(body).isNotNull();
        assertThat(body.length).isGreaterThan(0);
        assertThat(body[0]).isEqualTo((byte) 'P');
        assertThat(body[1]).isEqualTo((byte) 'K');
    }

    @Test
    void manifest_with_valid_token_succeeds() {
        WebTestClient authed = authedAdminClient();

        authed.get()
                .uri("/api/v1/data-sync/manifest")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.version").isEqualTo(DataSyncContract.VERSION);
    }
}
