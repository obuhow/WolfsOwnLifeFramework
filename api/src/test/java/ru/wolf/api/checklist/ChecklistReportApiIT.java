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
package ru.wolf.api.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.focus.FocusSession;
import ru.wolf.api.focus.FocusSessionRepository;
import ru.wolf.api.focus.FocusDistraction;
import ru.wolf.api.focus.FocusDistractionRepository;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;
import ru.wolf.api.support.ApiIntegrationTest;

class ChecklistReportApiIT extends ApiIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired UserRepository users;
    @org.springframework.beans.factory.annotation.Autowired DeloRepository delos;
    @org.springframework.beans.factory.annotation.Autowired FocusSessionRepository sessions;
    @org.springframework.beans.factory.annotation.Autowired FocusDistractionRepository distractions;

    @org.junit.jupiter.api.BeforeEach
    void cleanup() { distractions.deleteAll(); sessions.deleteAll(); }
    @Test
    void report_contains_empty_days_items_and_focus_switches_and_exports_markdown() {
        WebTestClient client = authedAdminClient();
        ru.wolf.api.checklist.dto.Response first = client.post().uri("/api/v1/checklist").contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("date", "2026-08-18", "title", "Проверить почту")).exchange().expectStatus().isCreated().expectBody(ru.wolf.api.checklist.dto.Response.class).returnResult().getResponseBody();
        client.patch().uri("/api/v1/checklist/{id}", first.id()).contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("done", true)).exchange().expectStatus().isOk();
        client.post().uri("/api/v1/checklist").contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("date", "2026-08-19", "title", "Разобрать входящие")).exchange().expectStatus().isCreated();
        User user = users.findByUsername("admin").orElseThrow();
        Delo delo = Delo.builder().user(user).title("Срочное переключение").executionMode(Delo.ExecutionMode.SELF).build();
        delo = delos.save(delo);
        FocusSession session = sessions.save(FocusSession.builder().user(user).delo(delo).startedAt(LocalDateTime.of(2026, 8, 19, 10, 0)).endedAt(LocalDateTime.of(2026, 8, 19, 11, 0)).build());
        distractions.save(FocusDistraction.builder().session(session).text("review").at(LocalDateTime.of(2026, 8, 19, 10, 20)).minutes(5).build());
        distractions.save(FocusDistraction.builder().session(session).text("message").at(LocalDateTime.of(2026, 8, 19, 10, 40)).minutes(null).build());
        client.get().uri("/api/v1/reports/checklist?from=2026-08-18&to=2026-08-20").exchange().expectStatus().isOk().expectBody(ru.wolf.api.checklist.dto.ReportResponse.class).value(report -> { assertThat(report.days()).hasSize(3); assertThat(report.days().get(2).items()).isEmpty(); assertThat(report.days().get(1).distractions()).hasSize(2); assertThat(report.checkedTotal()).isEqualTo(1); assertThat(report.itemsTotal()).isEqualTo(2); });
        client.get().uri("/api/v1/reports/checklist/export?format=md&from=2026-08-18&to=2026-08-20").exchange().expectStatus().isOk().expectHeader().contentTypeCompatibleWith(MediaType.TEXT_MARKDOWN).expectBody(String.class).value(text -> assertThat(text).contains("Проверить почту", "2026-08-20"));
    }
}
