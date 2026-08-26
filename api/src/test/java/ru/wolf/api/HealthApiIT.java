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
package ru.wolf.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.wolf.api.support.ApiIntegrationTest;

/**
 * Template integration test for WOLF API tickets.
 *
 * <p>Style for later tickets:
 * <ul>
 *   <li>Extend {@link ApiIntegrationTest} (Spring + Testcontainers Postgres + WebTestClient)</li>
 *   <li>Assert external HTTP behaviour (not private methods / SQL shape)</li>
 *   <li>Use glossary vocabulary in names when domain appears</li>
 *   <li>From ticket 02: obtain JWT and call via {@code authedClient()} / bearer helpers</li>
 * </ul>
 */
class HealthApiIT extends ApiIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void health_returns_up_when_postgres_is_reachable() {
        Integer one = jdbc.queryForObject("select 1", Integer.class);
        assertThat(one).isEqualTo(1);

        webTestClient
                .get()
                .uri("/api/v1/health")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("UP");
    }
}
