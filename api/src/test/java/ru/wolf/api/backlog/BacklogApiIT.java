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
package ru.wolf.api.backlog;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.backlog.dto.*;
import ru.wolf.api.delo.dto.*;
import ru.wolf.api.support.ApiIntegrationTest;

class BacklogApiIT extends ApiIntegrationTest {
    @Test
    void month_item_is_separate_and_move_preserves_month() {
        WebTestClient client = authedAdminClient();
        ru.wolf.api.delo.dto.DeloResponse delo = client.post().uri("/api/v1/delos").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateDeloRequest("Месячное дело", null, ru.wolf.api.delo.Delo.ExecutionMode.SELF, List.of(), null))
                .exchange().expectStatus().isOk().expectBody(ru.wolf.api.delo.dto.DeloResponse.class).returnResult().getResponseBody();
        Long deloId = delo.id();
        String month = "2026-08";
        client.post().uri("/api/v1/backlog").contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("deloId", deloId, "scope", "MONTH", "period", month, "plannedHours", 3.5)).exchange().expectStatus().isCreated();
        client.get().uri("/api/v1/backlog?scope=month&period=2026-08").exchange().expectStatus().isOk().expectBodyList(Response.class).value(list -> assertThat(list).singleElement().satisfies(item -> { assertThat(item.scope()).isEqualTo("month"); assertThat(item.plannedHours()).isEqualByComparingTo("3.5"); }));
        client.get().uri("/api/v1/backlog?scope=week&period=2026-W34").exchange().expectStatus().isOk().expectBodyList(Response.class).value(list -> assertThat(list).isEmpty());
        Response monthItem = client.get().uri("/api/v1/backlog?scope=month&period=2026-08").exchange().expectBodyList(Response.class).returnResult().getResponseBody().get(0);
        client.post().uri("/api/v1/backlog/{id}/move-to-week", monthItem.id()).contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("week", "2026-W34")).exchange().expectStatus().isOk();
        client.get().uri("/api/v1/backlog?scope=week&period=2026-W34").exchange().expectStatus().isOk().expectBodyList(Response.class).value(list -> assertThat(list).hasSize(1));
        client.get().uri("/api/v1/backlog?scope=month&period=2026-08").exchange().expectStatus().isOk().expectBodyList(Response.class).value(list -> assertThat(list.get(0).movedToWeek()).isEqualTo("2026-W34"));
        client.post().uri("/api/v1/backlog").contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("deloId", deloId, "scope", "MONTH", "period", month)).exchange().expectStatus().isEqualTo(409);
    }
}
