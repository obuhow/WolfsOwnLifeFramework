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
package ru.wolf.api.delo;

import ru.wolf.api.delo.dto.*;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import ru.wolf.api.lifearea.dto.*;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.backlog.BacklogItemRepository;
import ru.wolf.api.project.dto.*;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.util.List;
import java.util.Map;

class DeloApiIT extends ApiIntegrationTest {

    @Autowired
    DeloRepository deloRepository;

    @Autowired
    DeloProjectRepository deloProjectRepository;

    @Autowired
    ProjectRepository projectRepository;

    @Autowired
    LifeAreaRepository lifeAreaRepository;

    @Autowired
    BacklogItemRepository backlogItemRepository;

    @Autowired
    TimeEntryRepository timeEntryRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanup() {
        backlogItemRepository.deleteAll();
        deloProjectRepository.deleteAll();
        timeEntryRepository.deleteAll();
        deloRepository.deleteAll();
        projectRepository.deleteAll();
        lifeAreaRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> !"admin".equals(u.getUsername()))
                .forEach(userRepository::delete);
    }

    @Test
    void list_delos_empty_for_new_user() {
        WebTestClient authed = authedAdminClient();

        authed.get()
                .uri("/api/v1/delos")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(DeloResponse.class)
                .hasSize(0);
    }

    @Test
    void create_delo_without_project_is_valid() {
        WebTestClient authed = authedAdminClient();

        var req = new CreateDeloRequest("Утренний бег", "Без проекта — рутина", Delo.ExecutionMode.SELF, null, null);

        DeloResponse created = authed.post()
                .uri("/api/v1/delos")
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.title()).isEqualTo("Утренний бег");
        assertThat(created.description()).isEqualTo("Без проекта — рутина");
        assertThat(created.executionMode()).isEqualTo(Delo.ExecutionMode.SELF);
        assertThat(created.projectIds()).isEmpty();
        assertThat(created.primaryProjectId()).isNull();
    }

    @Test
    void create_delo_with_projects_sets_primary() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        ProjectResponse p1 = createProject(authed, areaId, "WOLF");
        ProjectResponse p2 = createProject(authed, areaId, "API");

        var req = new CreateDeloRequest("Код-ревью", null, Delo.ExecutionMode.DELEGATABLE, List.of(p1.id(), p2.id()), p2.id());

        DeloResponse created = authed.post()
                .uri("/api/v1/delos")
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created.projectIds()).containsExactlyInAnyOrder(p1.id(), p2.id());
        assertThat(created.primaryProjectId()).isEqualTo(p2.id());
        assertThat(created.executionMode()).isEqualTo(Delo.ExecutionMode.DELEGATABLE);
    }

    @Test
    void create_delo_with_projects_without_primary_defaults_to_first() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        ProjectResponse p1 = createProject(authed, areaId, "WOLF");
        ProjectResponse p2 = createProject(authed, areaId, "API");

        var req = new CreateDeloRequest("Планирование", null, null, List.of(p1.id(), p2.id()), null);

        DeloResponse created = authed.post()
                .uri("/api/v1/delos")
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created.primaryProjectId()).isEqualTo(p1.id());
    }

    @Test
    void reject_primary_without_projects() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        ProjectResponse p1 = createProject(authed, areaId, "WOLF");

        var req = new CreateDeloRequest("Сломанное", null, null, null, p1.id());

        authed.post()
                .uri("/api/v1/delos")
                .bodyValue(req)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void reject_primary_not_in_project_list() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        ProjectResponse p1 = createProject(authed, areaId, "WOLF");
        ProjectResponse p2 = createProject(authed, areaId, "API");

        var req = new CreateDeloRequest("Сломанное", null, null, List.of(p1.id()), p2.id());

        authed.post()
                .uri("/api/v1/delos")
                .bodyValue(req)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void get_detail_includes_project_links() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        ProjectResponse p1 = createProject(authed, areaId, "WOLF");

        DeloResponse created = createDelo(authed, "Релиз", List.of(p1.id()), p1.id());

        DeloDetailResponse detail = authed.get()
                .uri("/api/v1/delos/{id}", created.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloDetailResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(detail).isNotNull();
        assertThat(detail.title()).isEqualTo("Релиз");
        assertThat(detail.projects()).hasSize(1);
        assertThat(detail.projects().get(0).id()).isEqualTo(p1.id());
        assertThat(detail.projects().get(0).title()).isEqualTo("WOLF");
        assertThat(detail.projects().get(0).isPrimary()).isTrue();
    }

    @Test
    void update_delo_fields_and_links() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        ProjectResponse p1 = createProject(authed, areaId, "WOLF");
        ProjectResponse p2 = createProject(authed, areaId, "API");

        DeloResponse created = createDelo(authed, "Старое", List.of(p1.id()), p1.id());

        var update = new UpdateDeloRequest("Новое", "обновлено", Delo.ExecutionMode.AUTOMATABLE, List.of(p2.id()), p2.id());

        DeloResponse updated = authed.put()
                .uri("/api/v1/delos/{id}", created.id())
                .bodyValue(update)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(updated.title()).isEqualTo("Новое");
        assertThat(updated.description()).isEqualTo("обновлено");
        assertThat(updated.executionMode()).isEqualTo(Delo.ExecutionMode.AUTOMATABLE);
        assertThat(updated.projectIds()).containsExactly(p2.id());
        assertThat(updated.primaryProjectId()).isEqualTo(p2.id());
    }

    @Test
    void link_unlink_and_set_primary() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        ProjectResponse p1 = createProject(authed, areaId, "WOLF");
        ProjectResponse p2 = createProject(authed, areaId, "API");

        DeloResponse delo = createDelo(authed, "Связи", List.of(), null);

        DeloResponse linked1 = authed.post()
                .uri("/api/v1/delos/{deloId}/link/{projectId}", delo.id(), p1.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(linked1.projectIds()).containsExactly(p1.id());
        assertThat(linked1.primaryProjectId()).isEqualTo(p1.id());

        DeloResponse linked2 = authed.post()
                .uri("/api/v1/delos/{deloId}/link/{projectId}", delo.id(), p2.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(linked2.projectIds()).containsExactlyInAnyOrder(p1.id(), p2.id());
        assertThat(linked2.primaryProjectId()).isEqualTo(p1.id());

        DeloResponse primary = authed.put()
                .uri("/api/v1/delos/{deloId}/primary/{projectId}", delo.id(), p2.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(primary.primaryProjectId()).isEqualTo(p2.id());

        DeloResponse unlinked = authed.delete()
                .uri("/api/v1/delos/{deloId}/link/{projectId}", delo.id(), p2.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(unlinked.projectIds()).containsExactly(p1.id());
        assertThat(unlinked.primaryProjectId()).isEqualTo(p1.id());
    }

    @Test
    void project_detail_lists_attached_delos() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        ProjectResponse project = createProject(authed, areaId, "WOLF");
        DeloResponse delo = createDelo(authed, "Тикет 06", List.of(project.id()), project.id());

        ProjectDetailResponse detail = authed.get()
                .uri("/api/v1/projects/{id}", project.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProjectDetailResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(detail.delos()).hasSize(1);
        assertThat(detail.delos().get(0).id()).isEqualTo(delo.id());
        assertThat(detail.delos().get(0).title()).isEqualTo("Тикет 06");
        assertThat(detail.delos().get(0).isPrimary()).isTrue();
    }

    @Test
    void delete_delo() {
        WebTestClient authed = authedAdminClient();
        DeloResponse created = createDelo(authed, "Временное", List.of(), null);

        authed.delete()
                .uri("/api/v1/delos/{id}", created.id())
                .exchange()
                .expectStatus().isNoContent();

        authed.get()
                .uri("/api/v1/delos")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(DeloResponse.class)
                .hasSize(0);
    }

    @Test
    void isolation_between_users() {
        WebTestClient admin = authedAdminClient();
        Long adminArea = createLifeArea(admin, "Работа admin");
        ProjectResponse adminProject = createProject(admin, adminArea, "Секрет");
        DeloResponse adminDelo = createDelo(admin, "Секретное Дело", List.of(adminProject.id()), adminProject.id());

        User user2 = new User();
        user2.setUsername("user2");
        user2.setPasswordHash(passwordEncoder.encode("admin"));
        user2.setTimezone("Europe/Moscow");
        user2.setNightStart(java.time.LocalTime.of(23, 0));
        user2.setNightEnd(java.time.LocalTime.of(7, 0));
        user2.setHourAccountingMode("PRIMARY_ONLY");
        userRepository.save(user2);

        String token2 = login("user2", "admin");
        WebTestClient client2 = webTestClient.mutate()
                .defaultHeader("Authorization", "Bearer " + token2)
                .build();

        Long area2 = createLifeArea(client2, "Работа user2");
        ProjectResponse p2 = createProject(client2, area2, "Проект user2");
        createDelo(client2, "Дело user2", List.of(p2.id()), p2.id());

        client2.get()
                .uri("/api/v1/delos")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(DeloResponse.class)
                .hasSize(1);

        admin.get()
                .uri("/api/v1/delos")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(DeloResponse.class)
                .value(list -> assertThat(list).extracting(DeloResponse::title)
                        .containsExactly("Секретное Дело"));

        client2.get()
                .uri("/api/v1/delos/{id}", adminDelo.id())
                .exchange()
                .expectStatus().isBadRequest();

        client2.post()
                .uri("/api/v1/delos/{deloId}/link/{projectId}", adminDelo.id(), p2.id())
                .exchange()
                .expectStatus().isBadRequest();

        client2.put()
                .uri("/api/v1/delos/{id}", adminDelo.id())
                .bodyValue(Map.of(
                        "title", "hack",
                        "executionMode", "SELF",
                        "projectIds", List.of()
                ))
                .exchange()
                .expectStatus().isBadRequest();

        client2.delete()
                .uri("/api/v1/delos/{id}", adminDelo.id())
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void import_csv_creates_delos_and_adds_them_to_current_week_backlog() {
        WebTestClient authed = authedAdminClient();
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", "title,date,startAt,endAt,description,executionMode,projects,lifeArea\nИмпорт 1,2026-08-15,09:00,10:00,Описание,SELF,\nИмпорт 2,2026-08-15,10:15,10:30,,DELEGATABLE,,\n")
                .filename("delos.csv")
                .header("Content-Type", "text/csv");
        body.part("addToCurrentWeek", "true");

        authed.post()
                .uri("/api/v1/delos/import")
                .body(BodyInserters.fromMultipartData(body.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ImportResponse.class)
                .value(response -> assertThat(response.imported()).isEqualTo(2));

        authed.get().uri("/api/v1/delos")
                .exchange().expectStatus().isOk()
                .expectBodyList(DeloResponse.class)
                .value(list -> assertThat(list).extracting(DeloResponse::title)
                        .containsExactly("Импорт 1", "Импорт 2"));

        authed.get().uri("/api/v1/backlog/week")
                .exchange().expectStatus().isOk()
                .expectBody(ru.wolf.api.backlog.dto.WeekBacklogResponse.class)
                .value(response -> assertThat(response.delos())
                        .extracting(ru.wolf.api.backlog.dto.DeloResponse::title)
                        .containsExactly("Импорт 1", "Импорт 2"));
    }

    @Test
    void unauthenticated_access_rejected() {
        webTestClient.get().uri("/api/v1/delos").exchange().expectStatus().isForbidden();
        webTestClient.post().uri("/api/v1/delos")
                .bodyValue(new CreateDeloRequest(null, null, null, null, null))
                .exchange().expectStatus().isForbidden();
        webTestClient.get().uri("/api/v1/delos/1").exchange().expectStatus().isForbidden();
        webTestClient.put().uri("/api/v1/delos/1")
                .bodyValue(new UpdateDeloRequest(null, null, null, null, null))
                .exchange().expectStatus().isForbidden();
        webTestClient.delete().uri("/api/v1/delos/1").exchange().expectStatus().isForbidden();
    }

    private Long createLifeArea(WebTestClient client, String name) {
        var req = new CreateLifeAreaRequest(name, "#3d5a4a");
        LifeAreaResponse created = client.post()
                .uri("/api/v1/life-areas")
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LifeAreaResponse.class)
                .returnResult()
                .getResponseBody();
        return created.id();
    }

    private ProjectResponse createProject(WebTestClient client, Long lifeAreaId, String title) {
        var req = new CreateProjectRequest(lifeAreaId, title);
        return client.post()
                .uri("/api/v1/projects")
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProjectResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private DeloResponse createDelo(
            WebTestClient client,
            String title,
            List<Long> projectIds,
            Long primaryProjectId
    ) {
        var req = new CreateDeloRequest(title, null, Delo.ExecutionMode.SELF, projectIds, primaryProjectId);
        return client.post()
                .uri("/api/v1/delos")
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloResponse.class)
                .returnResult()
                .getResponseBody();
    }
}
