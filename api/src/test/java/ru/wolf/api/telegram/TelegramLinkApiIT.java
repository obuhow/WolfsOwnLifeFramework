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
package ru.wolf.api.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.telegram.dto.TelegramLinkStatus;

/**
 * Release 1.0, ticket 04 (closes Б-4): {@code POST /api/v1/bot/telegram/link} must issue a
 * one-time link token under JWT (previously missing → 403 for the Settings "Привязать Telegram"
 * button). Complements the existing {@code GET /link} status endpoint.
 */
class TelegramLinkApiIT extends ApiIntegrationTest {

    @Test
    void post_link_issues_pending_token_under_jwt() {
        WebTestClient authed = authedAdminClient();

        TelegramLinkStatus status = authed.post()
                .uri("/api/v1/bot/telegram/link")
                .exchange()
                .expectStatus().isOk()
                .expectBody(TelegramLinkStatus.class)
                .returnResult()
                .getResponseBody();

        assertThat(status).isNotNull();
        assertThat(status.linked()).isFalse();
        assertThat(status.pendingToken()).isNotBlank();
    }

    @Test
    void post_link_without_jwt_is_forbidden() {
        webTestClient.post()
                .uri("/api/v1/bot/telegram/link")
                .exchange()
                .expectStatus().isForbidden();
    }
}
