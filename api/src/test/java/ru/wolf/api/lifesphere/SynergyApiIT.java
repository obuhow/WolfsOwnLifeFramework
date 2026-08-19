package ru.wolf.api.lifesphere;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.lifearea.LifeArea;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.time.LocalDate;
import java.util.List;

class SynergyApiIT extends ApiIntegrationTest {

    @Autowired
    SynergyRepository synergyRepository;

    @Autowired
    LifeSphereRepository lifeSphereRepository;

    @Autowired
    LifeAreaRepository lifeAreaRepository;

    @Autowired
    ProjectRepository projectRepository;

    @Autowired
    UserRepository userRepository;

    private User adminUser;
    private LifeArea defaultLifeArea;
    private LifeSphere sphereHealth;
    private LifeSphere sphereMoney;
    private Project projectTest;

    @BeforeEach
    void setup() {
        synergyRepository.deleteAll();
        lifeSphereRepository.deleteAll();
        projectRepository.deleteAll();
        lifeAreaRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> !"admin".equals(u.getUsername()))
                .forEach(userRepository::delete);

        adminUser = userRepository.findByUsername("admin").orElseThrow();

        // Seed life spheres for admin user
        seedLifeSpheres(adminUser);

        // Fetch seeded spheres
        List<LifeSphere> spheres = lifeSphereRepository.findByUserOrderBySortOrderAscNameAsc(adminUser);
        sphereHealth = spheres.stream().filter(s -> s.getName().equals("Здоровье")).findFirst().orElseThrow();
        sphereMoney = spheres.stream().filter(s -> s.getName().equals("Ресурсы/деньги")).findFirst().orElseThrow();

        defaultLifeArea = lifeAreaRepository.save(LifeArea.builder()
                .user(adminUser)
                .name("Работа")
                .sortOrder(0)
                .color("#333333")
                .build());

        projectTest = projectRepository.save(Project.builder()
                .user(adminUser)
                .lifeArea(defaultLifeArea)
                .title("Тестовый проект")
                .description("Описание")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(10))
                .build());
    }

    private void seedLifeSpheres(User user) {
        String[][] spheres = {
                {"Здоровье", "0", "#EF4444"},
                {"Навык QA Java", "1", "#3B82F6"},
                {"Навык музыканта", "2", "#8B5CF6"},
                {"Общение на расстоянии", "3", "#06B6D4"},
                {"Мотивация к делам", "4", "#F59E0B"},
                {"Ресурсы/деньги", "5", "#10B981"},
                {"Ресурсы/время", "6", "#84CC16"},
                {"Открытие новых ходов", "7", "#EC4899"},
                {"Независимость", "8", "#6366F1"}
        };

        for (String[] s : spheres) {
            LifeSphere sphere = LifeSphere.builder()
                    .user(user)
                    .name(s[0])
                    .sortOrder(Integer.parseInt(s[1]))
                    .color(s[2])
                    .build();
            lifeSphereRepository.save(sphere);
        }
    }

    @Test
    void create_synergy_with_project_and_sphere_persists() {
        WebTestClient authed = authedAdminClient();

        var request = new SynergyController.CreateSynergyRequest();
        request.setSphereId(sphereHealth.getId());
        request.setProjectId(projectTest.getId());
        request.setImpact(Synergy.Impact.POSITIVE); // +1

        SynergyController.SynergyResponse created = authed.post()
                .uri("/api/v1/synergies")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(SynergyController.SynergyResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getProjectId()).isEqualTo(projectTest.getId());
        assertThat(created.getSphereId()).isEqualTo(sphereHealth.getId());
        assertThat(created.getSphereName()).isEqualTo("Здоровье");
        assertThat(created.getImpact()).isEqualTo(Synergy.Impact.POSITIVE);
    }

    @Test
    void duplicate_project_sphere_synergy_returns_409() {
        WebTestClient authed = authedAdminClient();

        var request = new SynergyController.CreateSynergyRequest();
        request.setSphereId(sphereHealth.getId());
        request.setProjectId(projectTest.getId());
        request.setImpact(Synergy.Impact.POSITIVE);

        authed.post().uri("/api/v1/synergies").bodyValue(request).exchange().expectStatus().isOk();

        // Duplicate
        authed.post().uri("/api/v1/synergies").bodyValue(request).exchange().expectStatus().isBadRequest();
    }

    @Test
    void get_synergies_filtered_by_project() {
        WebTestClient authed = authedAdminClient();

        // Create synergy with health (+) and money (-)
        var req1 = new SynergyController.CreateSynergyRequest();
        req1.setSphereId(sphereHealth.getId());
        req1.setProjectId(projectTest.getId());
        req1.setImpact(Synergy.Impact.POSITIVE);

        var req2 = new SynergyController.CreateSynergyRequest();
        req2.setSphereId(sphereMoney.getId());
        req2.setProjectId(projectTest.getId());
        req2.setImpact(Synergy.Impact.NEGATIVE);

        authed.post().uri("/api/v1/synergies").bodyValue(req1).exchange().expectStatus().isOk();
        authed.post().uri("/api/v1/synergies").bodyValue(req2).exchange().expectStatus().isOk();

        List<SynergyController.SynergyResponse> synergies = authed.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/synergies").queryParam("projectId", projectTest.getId()).build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(SynergyController.SynergyResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(synergies).hasSize(2);
        assertThat(synergies).anyMatch(s -> s.getSphereId().equals(sphereHealth.getId()) && s.getImpact() == Synergy.Impact.POSITIVE);
        assertThat(synergies).anyMatch(s -> s.getSphereId().equals(sphereMoney.getId()) && s.getImpact() == Synergy.Impact.NEGATIVE);
    }

    @Test
    void update_synergy_impact() {
        WebTestClient authed = authedAdminClient();

        var request = new SynergyController.CreateSynergyRequest();
        request.setSphereId(sphereHealth.getId());
        request.setProjectId(projectTest.getId());
        request.setImpact(Synergy.Impact.POSITIVE);

        SynergyController.SynergyResponse created = authed.post()
                .uri("/api/v1/synergies")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(SynergyController.SynergyResponse.class)
                .returnResult()
                .getResponseBody();

        var updateReq = new SynergyController.UpdateSynergyRequest();
        updateReq.setImpact(Synergy.Impact.NEUTRAL);

        SynergyController.SynergyResponse updated = authed.put()
                .uri("/api/v1/synergies/{id}", created.getId())
                .bodyValue(updateReq)
                .exchange()
                .expectStatus().isOk()
                .expectBody(SynergyController.SynergyResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(updated.getImpact()).isEqualTo(Synergy.Impact.NEUTRAL);
    }

    @Test
    void delete_synergy() {
        WebTestClient authed = authedAdminClient();

        var request = new SynergyController.CreateSynergyRequest();
        request.setSphereId(sphereHealth.getId());
        request.setProjectId(projectTest.getId());
        request.setImpact(Synergy.Impact.POSITIVE);

        SynergyController.SynergyResponse created = authed.post()
                .uri("/api/v1/synergies")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(SynergyController.SynergyResponse.class)
                .returnResult()
                .getResponseBody();

        authed.delete().uri("/api/v1/synergies/{id}", created.getId()).exchange().expectStatus().isNoContent();

        List<SynergyController.SynergyResponse> list = authed.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/synergies").queryParam("projectId", projectTest.getId()).build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(SynergyController.SynergyResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(list).isEmpty();
    }
}
