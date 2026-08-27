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
package ru.wolf.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.lifearea.LifeAreaController;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.project.dto.*;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

class ProjectApiIT extends ApiIntegrationTest {

    @Autowired
    ProjectRepository projectRepository;

    @Autowired
    DeloRepository deloRepository;

    @Autowired
    DeloProjectRepository deloProjectRepository;

    @Autowired
    ProjectDependencyRepository projectDependencyRepository;

    @Autowired
    LifeAreaRepository lifeAreaRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanup() {
        projectDependencyRepository.deleteAll();
        deloProjectRepository.deleteAll();
        deloRepository.deleteAll();
        projectRepository.deleteAll();
        lifeAreaRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> !"admin".equals(u.getUsername()))
                .forEach(userRepository::delete);
    }

    @Test
    void list_projects_empty_for_new_user() {
        WebTestClient authed = authedAdminClient();

        authed.get()
                .uri("/api/v1/projects")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ProjectResponse.class)
                .hasSize(0);
    }

    @Test
    void create_root_project_in_life_area() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");

        var req = new CreateProjectRequest(areaId, null, "WOLF 0.1", null, "Календарная ОС",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30), new BigDecimal("40.00"), null);

        ProjectResponse created = authed.post()
                .uri("/api/v1/projects")
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProjectResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.lifeAreaId()).isEqualTo(areaId);
        assertThat(created.parentId()).isNull();
        assertThat(created.title()).isEqualTo("WOLF 0.1");
        assertThat(created.description()).isEqualTo("Календарная ОС");
        assertThat(created.startDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(created.endDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(created.totalPlanHours()).isEqualByComparingTo("40.00");
    }

    @Test
    void create_nested_project_under_parent() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");

        ProjectResponse root = createProject(authed, areaId, null, "Корень");
        ProjectResponse child = createProject(authed, areaId, root.id(), "Подпроект");

        assertThat(child.parentId()).isEqualTo(root.id());
        assertThat(child.lifeAreaId()).isEqualTo(areaId);

        List<ProjectResponse> all = authed.get()
                .uri("/api/v1/projects")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ProjectResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(all).hasSize(2);
    }

    @Test
    void filter_projects_by_life_area() {
        WebTestClient authed = authedAdminClient();
        Long workId = createLifeArea(authed, "Работа");
        Long healthId = createLifeArea(authed, "Здоровье");

        createProject(authed, workId, null, "API");
        createProject(authed, healthId, null, "Бег");

        List<ProjectResponse> workProjects = authed.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/projects").queryParam("lifeAreaId", workId).build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ProjectResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(workProjects).hasSize(1);
        assertThat(workProjects.get(0).title()).isEqualTo("API");
    }

    @Test
    void get_project_detail_has_placeholders() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        ProjectResponse created = createProject(authed, areaId, null, "WOLF");

        ProjectDetailResponse detail = authed.get()
                .uri("/api/v1/projects/{id}", created.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProjectDetailResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(detail).isNotNull();
        assertThat(detail.title()).isEqualTo("WOLF");
        assertThat(detail.lifeAreaName()).isEqualTo("Работа");
        assertThat(detail.delos()).isEmpty();
        assertThat(detail.aggregates()).isNotNull();
        assertThat(detail.aggregates().getTotalFactHours()).isEqualByComparingTo("0.00");
        assertThat(detail.aggregates().getByDay()).isEmpty();
    }

    @Test
    void update_project_fields_and_parent() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        ProjectResponse root = createProject(authed, areaId, null, "Корень");
        ProjectResponse child = createProject(authed, areaId, null, "Лист");

        var update = new UpdateProjectRequest(areaId, root.id(), "Лист v2", null, "Описание",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1), new BigDecimal("12.5"), null);

        ProjectResponse updated = authed.put()
                .uri("/api/v1/projects/{id}", child.id())
                .bodyValue(update)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProjectResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(updated.title()).isEqualTo("Лист v2");
        assertThat(updated.parentId()).isEqualTo(root.id());
        assertThat(updated.totalPlanHours()).isEqualByComparingTo("12.5");
    }

    @Test
    void reject_parent_from_other_life_area() {
        WebTestClient authed = authedAdminClient();
        Long workId = createLifeArea(authed, "Работа");
        Long healthId = createLifeArea(authed, "Здоровье");
        ProjectResponse workRoot = createProject(authed, workId, null, "API");

        var req = new CreateProjectRequest(healthId, workRoot.id(), "Неверно", null, null, null, null, null, null);

        authed.post()
                .uri("/api/v1/projects")
                .bodyValue(req)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void reject_cycle_when_setting_parent() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        ProjectResponse a = createProject(authed, areaId, null, "A");
        ProjectResponse b = createProject(authed, areaId, a.id(), "B");
        ProjectResponse c = createProject(authed, areaId, b.id(), "C");

        // try make A child of C → cycle
        var update = new UpdateProjectRequest(areaId, c.id(), "A", null, null, null, null, null, null);

        authed.put()
                .uri("/api/v1/projects/{id}", a.id())
                .bodyValue(update)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void project_dependencies_list_add_and_delete() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        ProjectResponse blocker = createProject(authed, areaId, null, "Блокер");
        ProjectResponse blocked = createProject(authed, areaId, null, "Зависимый");

        DependenciesResponse response = authed.post()
                .uri("/api/v1/projects/{id}/dependencies", blocked.id())
                .bodyValue(new AddDependencyRequest(blocker.id()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(DependenciesResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response.blockedBy()).extracting(ProjectSummary::title)
                .containsExactly("Блокер");
        assertThat(response.blocks()).isEmpty();

        authed.get()
                .uri("/api/v1/projects/{id}/dependencies", blocker.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(DependenciesResponse.class)
                .value(body -> assertThat(body.blocks())
                        .extracting(ProjectSummary::title)
                        .containsExactly("Зависимый"));

        authed.delete()
                .uri("/api/v1/projects/{id}/dependencies/{blockerId}", blocked.id(), blocker.id())
                .exchange()
                .expectStatus().isNoContent();

        addDependency(authed, blocked.id(), blocker.id());
    }

    @Test
    void delete_dependency_allows_chain_rewire() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        ProjectResponse a = createProject(authed, areaId, null, "A");
        ProjectResponse b = createProject(authed, areaId, null, "B");
        ProjectResponse c = createProject(authed, areaId, null, "C");

        addDependency(authed, b.id(), a.id());
        addDependency(authed, c.id(), b.id());

        authed.delete()
                .uri("/api/v1/projects/{id}/dependencies/{blockerId}", c.id(), b.id())
                .exchange()
                .expectStatus().isNoContent();

        addDependency(authed, c.id(), a.id());
    }

    @Test
    void reject_direct_dependency_cycle_with_path() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        ProjectResponse a = createProject(authed, areaId, null, "A");
        ProjectResponse b = createProject(authed, areaId, null, "B");

        addDependency(authed, b.id(), a.id());

        String message = authed.post()
                .uri("/api/v1/projects/{id}/dependencies", a.id())
                .bodyValue(new AddDependencyRequest(b.id()))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("message")
                .toString();

        assertThat(message).contains("Цикл зависимостей", "B", "A", "B");
    }

    @Test
    void reject_dependency_cycle_with_path() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        ProjectResponse a = createProject(authed, areaId, null, "A");
        ProjectResponse b = createProject(authed, areaId, null, "B");
        ProjectResponse c = createProject(authed, areaId, null, "C");

        addDependency(authed, b.id(), a.id());
        addDependency(authed, c.id(), b.id());

        String message = authed.post()
                .uri("/api/v1/projects/{id}/dependencies", a.id())
                .bodyValue(new AddDependencyRequest(c.id()))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("message")
                .toString();

        assertThat(message).contains("Цикл зависимостей", "C", "A", "B", "C");
    }

    @Test
    void reject_end_before_start() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");

        var req = new CreateProjectRequest(areaId, null, "Плохие даты", null, null,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1), null, null);

        authed.post()
                .uri("/api/v1/projects")
                .bodyValue(req)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void reject_unknown_life_area() {
        WebTestClient authed = authedAdminClient();

        var req = new CreateProjectRequest(99999L, null, "Сирота", null, null, null, null, null, null);

        authed.post()
                .uri("/api/v1/projects")
                .bodyValue(req)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void delete_project_cascades_children() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        ProjectResponse root = createProject(authed, areaId, null, "Корень");
        createProject(authed, areaId, root.id(), "Дитя");

        authed.delete()
                .uri("/api/v1/projects/{id}", root.id())
                .exchange()
                .expectStatus().isNoContent();

        authed.get()
                .uri("/api/v1/projects")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ProjectResponse.class)
                .hasSize(0);
    }

    @Test
    void isolation_between_users() {
        WebTestClient admin = authedAdminClient();
        Long adminArea = createLifeArea(admin, "Работа admin");
        createProject(admin, adminArea, null, "Секрет admin");

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
        ProjectResponse p2 = createProject(client2, area2, null, "Проект user2");

        client2.get()
                .uri("/api/v1/projects")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ProjectResponse.class)
                .hasSize(1);

        admin.get()
                .uri("/api/v1/projects")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ProjectResponse.class)
                .value(list -> assertThat(list).extracting(ProjectResponse::title)
                        .containsExactly("Секрет admin"));

        // user2 cannot read admin's project by id
        Long adminProjectId = admin.get()
                .uri("/api/v1/projects")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ProjectResponse.class)
                .returnResult()
                .getResponseBody()
                .get(0)
                .id();

        client2.get()
                .uri("/api/v1/projects/{id}", adminProjectId)
                .exchange()
                .expectStatus().isBadRequest();

        client2.put()
                .uri("/api/v1/projects/{id}", adminProjectId)
                .bodyValue(Map.of(
                        "lifeAreaId", area2,
                        "title", "hack"
                ))
                .exchange()
                .expectStatus().isBadRequest();

        client2.delete()
                .uri("/api/v1/projects/{id}", adminProjectId)
                .exchange()
                .expectStatus().isBadRequest();

        // sanity: own project still there
        assertThat(p2.title()).isEqualTo("Проект user2");
    }

    @Test
    void unauthenticated_access_rejected() {
        webTestClient.get().uri("/api/v1/projects").exchange().expectStatus().isForbidden();
        webTestClient.post().uri("/api/v1/projects")
                .bodyValue(new CreateProjectRequest(null, null, null, null, null, null, null, null, null))
                .exchange().expectStatus().isForbidden();
        webTestClient.get().uri("/api/v1/projects/1").exchange().expectStatus().isForbidden();
        webTestClient.put().uri("/api/v1/projects/1")
                .bodyValue(new UpdateProjectRequest(null, null, null, null, null, null, null, null, null))
                .exchange().expectStatus().isForbidden();
        webTestClient.delete().uri("/api/v1/projects/1").exchange().expectStatus().isForbidden();
    }

    private Long createLifeArea(WebTestClient client, String name) {
        var req = new LifeAreaController.CreateLifeAreaRequest();
        req.setName(name);
        req.setColor("#3d5a4a");
        LifeAreaController.LifeAreaResponse areaResp = client.post()
                .uri("/api/v1/life-areas")
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LifeAreaController.LifeAreaResponse.class)
                .returnResult()
                .getResponseBody();
        return areaResp.getId();
    }

    private ProjectResponse createProject(
            WebTestClient client,
            Long lifeAreaId,
            Long parentId,
            String title
    ) {
        var req = new CreateProjectRequest(lifeAreaId, parentId, title, null, null, null, null, null, null);
        return client.post()
                .uri("/api/v1/projects")
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProjectResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private void addDependency(WebTestClient client, Long blockedId, Long blockerId) {
        client.post()
                .uri("/api/v1/projects/{id}/dependencies", blockedId)
                .bodyValue(new AddDependencyRequest(blockerId))
                .exchange()
                .expectStatus().isOk();
    }
}