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
import ru.wolf.api.lifesphere.dto.*;
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

        var request = new CreateSynergyRequest(sphereHealth.getId(), projectTest.getId(), null, null, Synergy.Impact.POSITIVE);

        SynergyResponse created = authed.post()
                .uri("/api/v1/synergies")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(SynergyResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.projectId()).isEqualTo(projectTest.getId());
        assertThat(created.sphereId()).isEqualTo(sphereHealth.getId());
        assertThat(created.sphereId()).isEqualTo("Здоровье");
        assertThat(created.impact()).isEqualTo(Synergy.Impact.POSITIVE);
    }

    @Test
    void duplicate_project_sphere_synergy_returns_409() {
        WebTestClient authed = authedAdminClient();

        var request = new CreateSynergyRequest(sphereHealth.getId(), projectTest.getId(), null, null, Synergy.Impact.POSITIVE);

        authed.post().uri("/api/v1/synergies").bodyValue(request).exchange().expectStatus().isOk();

        // Duplicate
        authed.post().uri("/api/v1/synergies").bodyValue(request).exchange().expectStatus().isBadRequest();
    }

    @Test
    void get_synergies_filtered_by_project() {
        WebTestClient authed = authedAdminClient();

        // Create synergy with health (+) and money (-)
        var req1 = new CreateSynergyRequest(sphereHealth.getId(), projectTest.getId(), null, null, Synergy.Impact.POSITIVE);
        var req2 = new CreateSynergyRequest(sphereMoney.getId(), projectTest.getId(), null, null, Synergy.Impact.NEGATIVE);

        authed.post().uri("/api/v1/synergies").bodyValue(req1).exchange().expectStatus().isOk();
        authed.post().uri("/api/v1/synergies").bodyValue(req2).exchange().expectStatus().isOk();

        List<SynergyResponse> synergies = authed.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/synergies").queryParam("projectId", projectTest.getId()).build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(SynergyResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(synergies).hasSize(2);
        assertThat(synergies).anyMatch(s -> s.sphereId().equals(sphereHealth.getId()) && s.impact() == Synergy.Impact.POSITIVE);
        assertThat(synergies).anyMatch(s -> s.sphereId().equals(sphereMoney.getId()) && s.impact() == Synergy.Impact.NEGATIVE);
    }

    @Test
    void update_synergy_impact() {
        WebTestClient authed = authedAdminClient();

        var request = new CreateSynergyRequest(sphereHealth.getId(), projectTest.getId(), null, null, Synergy.Impact.POSITIVE);

        SynergyResponse created = authed.post()
                .uri("/api/v1/synergies")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(SynergyResponse.class)
                .returnResult()
                .getResponseBody();

        var updateReq = new UpdateSynergyRequest(Synergy.Impact.NEUTRAL);

        SynergyResponse updated = authed.put()
                .uri("/api/v1/synergies/{id}", created.id())
                .bodyValue(updateReq)
                .exchange()
                .expectStatus().isOk()
                .expectBody(SynergyResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(updated.impact()).isEqualTo(Synergy.Impact.NEUTRAL);
    }

    @Test
    void delete_synergy() {
        WebTestClient authed = authedAdminClient();

        var request = new CreateSynergyRequest(sphereHealth.getId(), projectTest.getId(), null, null, Synergy.Impact.POSITIVE);

        SynergyResponse created = authed.post()
                .uri("/api/v1/synergies")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(SynergyResponse.class)
                .returnResult()
                .getResponseBody();

        authed.delete().uri("/api/v1/synergies/{id}", created.id()).exchange().expectStatus().isNoContent();

        List<SynergyResponse> list = authed.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/synergies").queryParam("projectId", projectTest.getId()).build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(SynergyResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(list).isEmpty();
    }
}
