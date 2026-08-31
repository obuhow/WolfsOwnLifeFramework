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
package ru.wolf.api.datasync;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import ru.wolf.api.lifearea.dto.CreateLifeAreaRequest;
import ru.wolf.api.lifearea.dto.LifeAreaResponse;
import ru.wolf.api.project.dto.CreateProjectRequest;
import ru.wolf.api.project.dto.ProjectResponse;
import ru.wolf.api.support.ApiIntegrationTest;

/**
 * Релиз 1.0, тикет 09: CSV-экспорт всех сущностей + обратный импорт (раунд-трип).
 *
 * <p>Экспорт CSV несёт тот же контракт {@link DataSyncContract}, что и xlsx: все листы
 * с идентичными заголовками. Импорт того же CSV восстанавливает сущности через
 * существующий пайплайн (upsert по externalId).
 */
class DataSyncCsvApiIT extends ApiIntegrationTest {

    @Test
    void export_csv_carries_all_sheet_headers_and_project_columns() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        // Родитель + вложенный ребёнок с датами и распределением — покрывает перенос вложенности.
        Long parentId = createProject(authed, new CreateProjectRequest(
                areaId, null, "WOLF 1.0", null, "Техдолг",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30),
                new BigDecimal("40.00"), ru.wolf.api.project.Project.PlanDistribution.EVEN_WEEKDAYS));
        createProject(authed, new CreateProjectRequest(
                areaId, parentId, "CSV-экспорт", null, "Тикет 09",
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 20),
                new BigDecimal("8.00"), ru.wolf.api.project.Project.PlanDistribution.EVEN_ALL_DAYS));

        byte[] body = authed.get()
                .uri("/api/v1/data-sync/export?format=csv")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("text/csv;charset=utf-8")
                .expectHeader().value(HttpHeaders.CONTENT_DISPOSITION, cd -> assertThat(cd).contains("wolf-data-0.21.csv"))
                .expectBody(byte[].class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        String csv = new String(body, StandardCharsets.UTF_8);

        // Манифест и все листы контракта присутствуют как секции.
        assertThat(csv).startsWith("# sheet:manifest");
        assertThat(csv).contains("format,wolf-data");
        assertThat(csv).contains("version,0.21");
        for (var sheet : DataSyncContract.manifest().sheets()) {
            assertThat(csv).as("секция листа " + sheet.name()).contains("# sheet:" + sheet.name());
            // Заголовок листа — те же колонки контракта.
            assertThat(csv).as("заголовок " + sheet.name()).contains(String.join(",", sheet.columns()));
        }
        // Ключевые колонки projects, закрывающие перенос вложенности/дат/распределения.
        assertThat(csv).contains("parentExternalId");
        assertThat(csv).contains("EVEN_WEEKDAYS");
        assertThat(csv).contains("2026-08-01");
    }

    @Test
    void csv_round_trips_back_through_import() {
        WebTestClient source = authedAdminClient();
        Long areaId = createLifeArea(source, "Здоровье");
        createProject(source, new CreateProjectRequest(
                areaId, null, "Раунд-трип CSV", null, "перенос",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                new BigDecimal("12.50"), ru.wolf.api.project.Project.PlanDistribution.NONE));

        byte[] csv = source.get()
                .uri("/api/v1/data-sync/export?format=csv")
                .exchange()
                .expectStatus().isOk()
                .expectBody(byte[].class)
                .returnResult()
                .getResponseBody();
        assertThat(csv).isNotNull();

        // Импорт того же CSV: preview должен распарсить наш CSV (через конвертацию в workbook),
        // насчитать сущности и разрешить применение (valid).
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", csv)
                .header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=file; filename=wolf-data-0.21.csv")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv");

        source.post()
                .uri("/api/v1/data-sync/import/preview")
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("VALID")
                .jsonPath("$.applyAllowed").isEqualTo(true)
                .jsonPath("$.counts.projects").isEqualTo(1)
                .jsonPath("$.counts.life_areas").isEqualTo(1);
    }

    private Long createLifeArea(WebTestClient client, String name) {
        LifeAreaResponse area = client.post()
                .uri("/api/v1/life-areas")
                .bodyValue(new CreateLifeAreaRequest(name, "#3d5a4a"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(LifeAreaResponse.class)
                .returnResult()
                .getResponseBody();
        return area.id();
    }

    private Long createProject(WebTestClient client, CreateProjectRequest req) {
        ProjectResponse project = client.post()
                .uri("/api/v1/projects")
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProjectResponse.class)
                .returnResult()
                .getResponseBody();
        return project.id();
    }
}
