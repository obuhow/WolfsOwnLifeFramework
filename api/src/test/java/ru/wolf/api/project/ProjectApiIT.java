package ru.wolf.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.lifearea.LifeAreaController;
import ru.wolf.api.lifearea.LifeAreaRepository;
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
    LifeAreaRepository lifeAreaRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanup() {
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
                .expectBodyList(ProjectController.ProjectResponse.class)
                .hasSize(0);
    }

    @Test
    void create_root_project_in_life_area() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");

        var req = new ProjectController.CreateProjectRequest();
        req.setLifeAreaId(areaId);
        req.setTitle("WOLF 0.1");
        req.setDescription("Календарная ОС");
        req.setStartDate(LocalDate.of(2026, 8, 1));
        req.setEndDate(LocalDate.of(2026, 9, 30));
        req.setTotalPlanHours(new BigDecimal("40.00"));

        ProjectController.ProjectResponse created = authed.post()
                .uri("/api/v1/projects")
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProjectController.ProjectResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getLifeAreaId()).isEqualTo(areaId);
        assertThat(created.getParentId()).isNull();
        assertThat(created.getTitle()).isEqualTo("WOLF 0.1");
        assertThat(created.getDescription()).isEqualTo("Календарная ОС");
        assertThat(created.getStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(created.getEndDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(created.getTotalPlanHours()).isEqualByComparingTo("40.00");
    }

    @Test
    void create_nested_project_under_parent() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");

        ProjectController.ProjectResponse root = createProject(authed, areaId, null, "Корень");
        ProjectController.ProjectResponse child = createProject(authed, areaId, root.getId(), "Подпроект");

        assertThat(child.getParentId()).isEqualTo(root.getId());
        assertThat(child.getLifeAreaId()).isEqualTo(areaId);

        List<ProjectController.ProjectResponse> all = authed.get()
                .uri("/api/v1/projects")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ProjectController.ProjectResponse.class)
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

        List<ProjectController.ProjectResponse> workProjects = authed.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/projects").queryParam("lifeAreaId", workId).build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ProjectController.ProjectResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(workProjects).hasSize(1);
        assertThat(workProjects.get(0).getTitle()).isEqualTo("API");
    }

    @Test
    void get_project_detail_has_placeholders() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        ProjectController.ProjectResponse created = createProject(authed, areaId, null, "WOLF");

        ProjectController.ProjectDetailResponse detail = authed.get()
                .uri("/api/v1/projects/{id}", created.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProjectController.ProjectDetailResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(detail).isNotNull();
        assertThat(detail.getTitle()).isEqualTo("WOLF");
        assertThat(detail.getLifeAreaName()).isEqualTo("Работа");
        assertThat(detail.getDelos()).isEmpty();
        assertThat(detail.getAggregates()).isNull();
    }

    @Test
    void update_project_fields_and_parent() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        ProjectController.ProjectResponse root = createProject(authed, areaId, null, "Корень");
        ProjectController.ProjectResponse child = createProject(authed, areaId, null, "Лист");

        var update = new ProjectController.UpdateProjectRequest();
        update.setLifeAreaId(areaId);
        update.setParentId(root.getId());
        update.setTitle("Лист v2");
        update.setDescription("Описание");
        update.setStartDate(LocalDate.of(2026, 1, 1));
        update.setEndDate(LocalDate.of(2026, 6, 1));
        update.setTotalPlanHours(new BigDecimal("12.5"));

        ProjectController.ProjectResponse updated = authed.put()
                .uri("/api/v1/projects/{id}", child.getId())
                .bodyValue(update)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProjectController.ProjectResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(updated.getTitle()).isEqualTo("Лист v2");
        assertThat(updated.getParentId()).isEqualTo(root.getId());
        assertThat(updated.getTotalPlanHours()).isEqualByComparingTo("12.5");
    }

    @Test
    void reject_parent_from_other_life_area() {
        WebTestClient authed = authedAdminClient();
        Long workId = createLifeArea(authed, "Работа");
        Long healthId = createLifeArea(authed, "Здоровье");
        ProjectController.ProjectResponse workRoot = createProject(authed, workId, null, "API");

        var req = new ProjectController.CreateProjectRequest();
        req.setLifeAreaId(healthId);
        req.setParentId(workRoot.getId());
        req.setTitle("Неверно");

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
        ProjectController.ProjectResponse a = createProject(authed, areaId, null, "A");
        ProjectController.ProjectResponse b = createProject(authed, areaId, a.getId(), "B");
        ProjectController.ProjectResponse c = createProject(authed, areaId, b.getId(), "C");

        // try make A child of C → cycle
        var update = new ProjectController.UpdateProjectRequest();
        update.setLifeAreaId(areaId);
        update.setParentId(c.getId());
        update.setTitle("A");

        authed.put()
                .uri("/api/v1/projects/{id}", a.getId())
                .bodyValue(update)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void reject_end_before_start() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");

        var req = new ProjectController.CreateProjectRequest();
        req.setLifeAreaId(areaId);
        req.setTitle("Плохие даты");
        req.setStartDate(LocalDate.of(2026, 6, 1));
        req.setEndDate(LocalDate.of(2026, 1, 1));

        authed.post()
                .uri("/api/v1/projects")
                .bodyValue(req)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void reject_unknown_life_area() {
        WebTestClient authed = authedAdminClient();

        var req = new ProjectController.CreateProjectRequest();
        req.setLifeAreaId(99999L);
        req.setTitle("Сирота");

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
        ProjectController.ProjectResponse root = createProject(authed, areaId, null, "Корень");
        createProject(authed, areaId, root.getId(), "Дитя");

        authed.delete()
                .uri("/api/v1/projects/{id}", root.getId())
                .exchange()
                .expectStatus().isNoContent();

        authed.get()
                .uri("/api/v1/projects")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ProjectController.ProjectResponse.class)
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
        ProjectController.ProjectResponse p2 = createProject(client2, area2, null, "Проект user2");

        client2.get()
                .uri("/api/v1/projects")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ProjectController.ProjectResponse.class)
                .hasSize(1);

        admin.get()
                .uri("/api/v1/projects")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ProjectController.ProjectResponse.class)
                .value(list -> assertThat(list).extracting(ProjectController.ProjectResponse::getTitle)
                        .containsExactly("Секрет admin"));

        // user2 cannot read admin's project by id
        Long adminProjectId = admin.get()
                .uri("/api/v1/projects")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ProjectController.ProjectResponse.class)
                .returnResult()
                .getResponseBody()
                .get(0)
                .getId();

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
        assertThat(p2.getTitle()).isEqualTo("Проект user2");
    }

    @Test
    void unauthenticated_access_rejected() {
        webTestClient.get().uri("/api/v1/projects").exchange().expectStatus().isForbidden();
        webTestClient.post().uri("/api/v1/projects")
                .bodyValue(new ProjectController.CreateProjectRequest())
                .exchange().expectStatus().isForbidden();
        webTestClient.get().uri("/api/v1/projects/1").exchange().expectStatus().isForbidden();
        webTestClient.put().uri("/api/v1/projects/1")
                .bodyValue(new ProjectController.UpdateProjectRequest())
                .exchange().expectStatus().isForbidden();
        webTestClient.delete().uri("/api/v1/projects/1").exchange().expectStatus().isForbidden();
    }

    private Long createLifeArea(WebTestClient client, String name) {
        var req = new LifeAreaController.CreateLifeAreaRequest();
        req.setName(name);
        req.setColor("#3d5a4a");
        LifeAreaController.LifeAreaResponse created = client.post()
                .uri("/api/v1/life-areas")
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LifeAreaController.LifeAreaResponse.class)
                .returnResult()
                .getResponseBody();
        return created.getId();
    }

    private ProjectController.ProjectResponse createProject(
            WebTestClient client,
            Long lifeAreaId,
            Long parentId,
            String title
    ) {
        var req = new ProjectController.CreateProjectRequest();
        req.setLifeAreaId(lifeAreaId);
        req.setParentId(parentId);
        req.setTitle(title);
        return client.post()
                .uri("/api/v1/projects")
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProjectController.ProjectResponse.class)
                .returnResult()
                .getResponseBody();
    }
}