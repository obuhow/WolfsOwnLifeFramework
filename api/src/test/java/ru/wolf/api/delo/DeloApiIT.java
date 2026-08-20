package ru.wolf.api.delo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import ru.wolf.api.lifearea.LifeAreaController;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.backlog.BacklogItemRepository;
import ru.wolf.api.project.ProjectController;
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
                .expectBodyList(DeloController.DeloResponse.class)
                .hasSize(0);
    }

    @Test
    void create_delo_without_project_is_valid() {
        WebTestClient authed = authedAdminClient();

        var req = new DeloController.CreateDeloRequest();
        req.setTitle("Утренний бег");
        req.setDescription("Без проекта — рутина");
        req.setExecutionMode(Delo.ExecutionMode.SELF);

        DeloController.DeloResponse created = authed.post()
                .uri("/api/v1/delos")
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloController.DeloResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getTitle()).isEqualTo("Утренний бег");
        assertThat(created.getDescription()).isEqualTo("Без проекта — рутина");
        assertThat(created.getExecutionMode()).isEqualTo(Delo.ExecutionMode.SELF);
        assertThat(created.getProjectIds()).isEmpty();
        assertThat(created.getPrimaryProjectId()).isNull();
    }

    @Test
    void create_delo_with_projects_sets_primary() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        ProjectController.ProjectResponse p1 = createProject(authed, areaId, "WOLF");
        ProjectController.ProjectResponse p2 = createProject(authed, areaId, "API");

        var req = new DeloController.CreateDeloRequest();
        req.setTitle("Код-ревью");
        req.setExecutionMode(Delo.ExecutionMode.DELEGATABLE);
        req.setProjectIds(List.of(p1.getId(), p2.getId()));
        req.setPrimaryProjectId(p2.getId());

        DeloController.DeloResponse created = authed.post()
                .uri("/api/v1/delos")
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloController.DeloResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created.getProjectIds()).containsExactlyInAnyOrder(p1.getId(), p2.getId());
        assertThat(created.getPrimaryProjectId()).isEqualTo(p2.getId());
        assertThat(created.getExecutionMode()).isEqualTo(Delo.ExecutionMode.DELEGATABLE);
    }

    @Test
    void create_delo_with_projects_without_primary_defaults_to_first() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        ProjectController.ProjectResponse p1 = createProject(authed, areaId, "WOLF");
        ProjectController.ProjectResponse p2 = createProject(authed, areaId, "API");

        var req = new DeloController.CreateDeloRequest();
        req.setTitle("Планирование");
        req.setProjectIds(List.of(p1.getId(), p2.getId()));

        DeloController.DeloResponse created = authed.post()
                .uri("/api/v1/delos")
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloController.DeloResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created.getPrimaryProjectId()).isEqualTo(p1.getId());
    }

    @Test
    void reject_primary_without_projects() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        ProjectController.ProjectResponse p1 = createProject(authed, areaId, "WOLF");

        var req = new DeloController.CreateDeloRequest();
        req.setTitle("Сломанное");
        req.setPrimaryProjectId(p1.getId());

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
        ProjectController.ProjectResponse p1 = createProject(authed, areaId, "WOLF");
        ProjectController.ProjectResponse p2 = createProject(authed, areaId, "API");

        var req = new DeloController.CreateDeloRequest();
        req.setTitle("Сломанное");
        req.setProjectIds(List.of(p1.getId()));
        req.setPrimaryProjectId(p2.getId());

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
        ProjectController.ProjectResponse p1 = createProject(authed, areaId, "WOLF");

        DeloController.DeloResponse created = createDelo(authed, "Релиз", List.of(p1.getId()), p1.getId());

        DeloController.DeloDetailResponse detail = authed.get()
                .uri("/api/v1/delos/{id}", created.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloController.DeloDetailResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(detail).isNotNull();
        assertThat(detail.getTitle()).isEqualTo("Релиз");
        assertThat(detail.getProjects()).hasSize(1);
        assertThat(detail.getProjects().get(0).getId()).isEqualTo(p1.getId());
        assertThat(detail.getProjects().get(0).getTitle()).isEqualTo("WOLF");
        assertThat(detail.getProjects().get(0).getIsPrimary()).isTrue();
    }

    @Test
    void update_delo_fields_and_links() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        ProjectController.ProjectResponse p1 = createProject(authed, areaId, "WOLF");
        ProjectController.ProjectResponse p2 = createProject(authed, areaId, "API");

        DeloController.DeloResponse created = createDelo(authed, "Старое", List.of(p1.getId()), p1.getId());

        var update = new DeloController.UpdateDeloRequest();
        update.setTitle("Новое");
        update.setDescription("обновлено");
        update.setExecutionMode(Delo.ExecutionMode.AUTOMATABLE);
        update.setProjectIds(List.of(p2.getId()));
        update.setPrimaryProjectId(p2.getId());

        DeloController.DeloResponse updated = authed.put()
                .uri("/api/v1/delos/{id}", created.getId())
                .bodyValue(update)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloController.DeloResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(updated.getTitle()).isEqualTo("Новое");
        assertThat(updated.getDescription()).isEqualTo("обновлено");
        assertThat(updated.getExecutionMode()).isEqualTo(Delo.ExecutionMode.AUTOMATABLE);
        assertThat(updated.getProjectIds()).containsExactly(p2.getId());
        assertThat(updated.getPrimaryProjectId()).isEqualTo(p2.getId());
    }

    @Test
    void link_unlink_and_set_primary() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        ProjectController.ProjectResponse p1 = createProject(authed, areaId, "WOLF");
        ProjectController.ProjectResponse p2 = createProject(authed, areaId, "API");

        DeloController.DeloResponse delo = createDelo(authed, "Связи", List.of(), null);

        DeloController.DeloResponse linked1 = authed.post()
                .uri("/api/v1/delos/{deloId}/link/{projectId}", delo.getId(), p1.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloController.DeloResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(linked1.getProjectIds()).containsExactly(p1.getId());
        assertThat(linked1.getPrimaryProjectId()).isEqualTo(p1.getId());

        DeloController.DeloResponse linked2 = authed.post()
                .uri("/api/v1/delos/{deloId}/link/{projectId}", delo.getId(), p2.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloController.DeloResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(linked2.getProjectIds()).containsExactlyInAnyOrder(p1.getId(), p2.getId());
        assertThat(linked2.getPrimaryProjectId()).isEqualTo(p1.getId());

        DeloController.DeloResponse primary = authed.put()
                .uri("/api/v1/delos/{deloId}/primary/{projectId}", delo.getId(), p2.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloController.DeloResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(primary.getPrimaryProjectId()).isEqualTo(p2.getId());

        DeloController.DeloResponse unlinked = authed.delete()
                .uri("/api/v1/delos/{deloId}/link/{projectId}", delo.getId(), p2.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloController.DeloResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(unlinked.getProjectIds()).containsExactly(p1.getId());
        assertThat(unlinked.getPrimaryProjectId()).isEqualTo(p1.getId());
    }

    @Test
    void project_detail_lists_attached_delos() {
        WebTestClient authed = authedAdminClient();
        Long areaId = createLifeArea(authed, "Работа");
        ProjectController.ProjectResponse project = createProject(authed, areaId, "WOLF");
        DeloController.DeloResponse delo = createDelo(authed, "Тикет 06", List.of(project.getId()), project.getId());

        ProjectController.ProjectDetailResponse detail = authed.get()
                .uri("/api/v1/projects/{id}", project.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProjectController.ProjectDetailResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(detail.getDelos()).hasSize(1);
        assertThat(detail.getDelos().get(0).getId()).isEqualTo(delo.getId());
        assertThat(detail.getDelos().get(0).getTitle()).isEqualTo("Тикет 06");
        assertThat(detail.getDelos().get(0).getIsPrimary()).isTrue();
    }

    @Test
    void delete_delo() {
        WebTestClient authed = authedAdminClient();
        DeloController.DeloResponse created = createDelo(authed, "Временное", List.of(), null);

        authed.delete()
                .uri("/api/v1/delos/{id}", created.getId())
                .exchange()
                .expectStatus().isNoContent();

        authed.get()
                .uri("/api/v1/delos")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(DeloController.DeloResponse.class)
                .hasSize(0);
    }

    @Test
    void isolation_between_users() {
        WebTestClient admin = authedAdminClient();
        Long adminArea = createLifeArea(admin, "Работа admin");
        ProjectController.ProjectResponse adminProject = createProject(admin, adminArea, "Секрет");
        DeloController.DeloResponse adminDelo = createDelo(admin, "Секретное Дело", List.of(adminProject.getId()), adminProject.getId());

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
        ProjectController.ProjectResponse p2 = createProject(client2, area2, "Проект user2");
        createDelo(client2, "Дело user2", List.of(p2.getId()), p2.getId());

        client2.get()
                .uri("/api/v1/delos")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(DeloController.DeloResponse.class)
                .hasSize(1);

        admin.get()
                .uri("/api/v1/delos")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(DeloController.DeloResponse.class)
                .value(list -> assertThat(list).extracting(DeloController.DeloResponse::getTitle)
                        .containsExactly("Секретное Дело"));

        client2.get()
                .uri("/api/v1/delos/{id}", adminDelo.getId())
                .exchange()
                .expectStatus().isBadRequest();

        client2.post()
                .uri("/api/v1/delos/{deloId}/link/{projectId}", adminDelo.getId(), p2.getId())
                .exchange()
                .expectStatus().isBadRequest();

        client2.put()
                .uri("/api/v1/delos/{id}", adminDelo.getId())
                .bodyValue(Map.of(
                        "title", "hack",
                        "executionMode", "SELF",
                        "projectIds", List.of()
                ))
                .exchange()
                .expectStatus().isBadRequest();

        client2.delete()
                .uri("/api/v1/delos/{id}", adminDelo.getId())
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
                .expectBody(DeloImportController.ImportResponse.class)
                .value(response -> assertThat(response.getImported()).isEqualTo(2));

        authed.get().uri("/api/v1/delos")
                .exchange().expectStatus().isOk()
                .expectBodyList(DeloController.DeloResponse.class)
                .value(list -> assertThat(list).extracting(DeloController.DeloResponse::getTitle)
                        .containsExactly("Импорт 1", "Импорт 2"));

        authed.get().uri("/api/v1/backlog/week")
                .exchange().expectStatus().isOk()
                .expectBody(ru.wolf.api.backlog.WeekBacklogController.WeekBacklogResponse.class)
                .value(response -> assertThat(response.getDelos())
                        .extracting(ru.wolf.api.backlog.WeekBacklogController.DeloResponse::getTitle)
                        .containsExactly("Импорт 1", "Импорт 2"));
    }

    @Test
    void unauthenticated_access_rejected() {
        webTestClient.get().uri("/api/v1/delos").exchange().expectStatus().isForbidden();
        webTestClient.post().uri("/api/v1/delos")
                .bodyValue(new DeloController.CreateDeloRequest())
                .exchange().expectStatus().isForbidden();
        webTestClient.get().uri("/api/v1/delos/1").exchange().expectStatus().isForbidden();
        webTestClient.put().uri("/api/v1/delos/1")
                .bodyValue(new DeloController.UpdateDeloRequest())
                .exchange().expectStatus().isForbidden();
        webTestClient.delete().uri("/api/v1/delos/1").exchange().expectStatus().isForbidden();
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

    private ProjectController.ProjectResponse createProject(WebTestClient client, Long lifeAreaId, String title) {
        var req = new ProjectController.CreateProjectRequest();
        req.setLifeAreaId(lifeAreaId);
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

    private DeloController.DeloResponse createDelo(
            WebTestClient client,
            String title,
            List<Long> projectIds,
            Long primaryProjectId
    ) {
        var req = new DeloController.CreateDeloRequest();
        req.setTitle(title);
        req.setExecutionMode(Delo.ExecutionMode.SELF);
        req.setProjectIds(projectIds);
        req.setPrimaryProjectId(primaryProjectId);
        return client.post()
                .uri("/api/v1/delos")
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeloController.DeloResponse.class)
                .returnResult()
                .getResponseBody();
    }
}
