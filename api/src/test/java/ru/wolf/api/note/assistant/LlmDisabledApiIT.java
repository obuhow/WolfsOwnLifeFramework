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
package ru.wolf.api.note.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.support.ApiIntegrationTest;

@ActiveProfiles("test")
@TestPropertySource(properties = "wolf.llm.enabled=false")
class LlmDisabledApiIT extends ApiIntegrationTest {

    @Test
    void llm_endpoints_return_service_unavailable_when_disabled() {
        WebTestClient client = authedAdminClient();
        client.get().uri("/api/v1/projects/{id}/resume", 1L)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.message").isEqualTo(
                        "LLM-функции отключены: установите wolf.llm.enabled=true");
    }
}
