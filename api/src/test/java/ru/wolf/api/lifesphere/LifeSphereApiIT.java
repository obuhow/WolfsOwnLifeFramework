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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.util.List;

class LifeSphereApiIT extends ApiIntegrationTest {

    @Autowired
    LifeSphereRepository lifeSphereRepository;

    @Autowired
    SynergyRepository synergyRepository;

    @Autowired
    ProjectRepository projectRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanup() {
        synergyRepository.deleteAll();
        lifeSphereRepository.deleteAll();
        projectRepository.deleteAll();
        // keep seed admin; remove extra users from previous tests
        userRepository.findAll().stream()
                .filter(u -> !"admin".equals(u.getUsername()))
                .forEach(userRepository::delete);

        // Reseed life spheres for admin user
        User admin = userRepository.findByUsername("admin").orElseThrow();
        seedLifeSpheres(admin);
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
    void get_life_spheres_returns_seeded_spheres_for_new_user() {
        WebTestClient authed = authedAdminClient();

        authed.get()
                .uri("/api/v1/life-spheres")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(LifeSphereController.LifeSphereResponse.class)
                .hasSize(9); // 9 seeded spheres
    }

    @Test
    void seeded_spheres_have_correct_names_and_order() {
        WebTestClient authed = authedAdminClient();

        List<LifeSphereController.LifeSphereResponse> all = authed.get()
                .uri("/api/v1/life-spheres")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(LifeSphereController.LifeSphereResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(all).hasSize(9);
        assertThat(all.get(0).getName()).isEqualTo("Здоровье");
        assertThat(all.get(0).getSortOrder()).isEqualTo(0);
        assertThat(all.get(0).getColor()).isEqualTo("#EF4444");
        assertThat(all.get(1).getName()).isEqualTo("Навык QA Java");
        assertThat(all.get(1).getSortOrder()).isEqualTo(1);
        assertThat(all.get(2).getName()).isEqualTo("Навык музыканта");
        assertThat(all.get(3).getName()).isEqualTo("Общение на расстоянии");
        assertThat(all.get(4).getName()).isEqualTo("Мотивация к делам");
        assertThat(all.get(5).getName()).isEqualTo("Ресурсы/деньги");
        assertThat(all.get(6).getName()).isEqualTo("Ресурсы/время");
        assertThat(all.get(7).getName()).isEqualTo("Открытие новых ходов");
        assertThat(all.get(8).getName()).isEqualTo("Независимость");
    }

    @Test
    void create_life_sphere_persists_and_returns() {
        WebTestClient authed = authedAdminClient();

        var request = new LifeSphereController.CreateLifeSphereRequest();
        request.setName("Тестовая сфера");
        request.setColor("#123456");

        LifeSphereController.LifeSphereResponse created = authed.post()
                .uri("/api/v1/life-spheres")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LifeSphereController.LifeSphereResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("Тестовая сфера");
        assertThat(created.getColor()).isEqualTo("#123456");
        assertThat(created.getSortOrder()).isEqualTo(9); // after seeded 9
        assertThat(created.isArchived()).isFalse();
    }

    @Test
    void create_multiple_life_spheres_gets_sequential_sort_order() {
        WebTestClient authed = authedAdminClient();

        var req1 = new LifeSphereController.CreateLifeSphereRequest();
        req1.setName("Сфера 1");
        req1.setColor("#111111");

        var req2 = new LifeSphereController.CreateLifeSphereRequest();
        req2.setName("Сфера 2");
        req2.setColor("#222222");

        var req3 = new LifeSphereController.CreateLifeSphereRequest();
        req3.setName("Сфера 3");
        req3.setColor("#333333");

        authed.post().uri("/api/v1/life-spheres").bodyValue(req1).exchange().expectStatus().isOk();
        authed.post().uri("/api/v1/life-spheres").bodyValue(req2).exchange().expectStatus().isOk();
        authed.post().uri("/api/v1/life-spheres").bodyValue(req3).exchange().expectStatus().isOk();

        List<LifeSphereController.LifeSphereResponse> all = authed.get()
                .uri("/api/v1/life-spheres")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(LifeSphereController.LifeSphereResponse.class)
                .returnResult()
                .getResponseBody();

        // 9 seeded + 3 created = 12 total
        assertThat(all).hasSize(12);
        // Last 3 should have sort orders 9, 10, 11
        assertThat(all.get(9).getName()).isEqualTo("Сфера 1");
        assertThat(all.get(9).getSortOrder()).isEqualTo(9);
        assertThat(all.get(10).getName()).isEqualTo("Сфера 2");
        assertThat(all.get(10).getSortOrder()).isEqualTo(10);
        assertThat(all.get(11).getName()).isEqualTo("Сфера 3");
        assertThat(all.get(11).getSortOrder()).isEqualTo(11);
    }

    @Test
    void duplicate_name_rejected() {
        WebTestClient authed = authedAdminClient();

        var request = new LifeSphereController.CreateLifeSphereRequest();
        request.setName("Здоровье"); // seeded name

        authed.post().uri("/api/v1/life-spheres").bodyValue(request).exchange().expectStatus().isBadRequest();
    }

    @Test
    void update_life_sphere_changes_name_and_color() {
        WebTestClient authed = authedAdminClient();

        var createReq = new LifeSphereController.CreateLifeSphereRequest();
        createReq.setName("Тест");
        createReq.setColor("#111111");

        LifeSphereController.LifeSphereResponse created = authed.post()
                .uri("/api/v1/life-spheres")
                .bodyValue(createReq)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LifeSphereController.LifeSphereResponse.class)
                .returnResult()
                .getResponseBody();

        var updateReq = new LifeSphereController.UpdateLifeSphereRequest();
        updateReq.setName("Обновлённая");
        updateReq.setColor("#222222");

        LifeSphereController.LifeSphereResponse updated = authed.put()
                .uri("/api/v1/life-spheres/{id}", created.getId())
                .bodyValue(updateReq)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LifeSphereController.LifeSphereResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(updated.getName()).isEqualTo("Обновлённая");
        assertThat(updated.getColor()).isEqualTo("#222222");
        assertThat(updated.getId()).isEqualTo(created.getId());
    }

    @Test
    void update_duplicate_name_rejected() {
        WebTestClient authed = authedAdminClient();

        var req1 = new LifeSphereController.CreateLifeSphereRequest();
        req1.setName("Сфера А");
        authed.post().uri("/api/v1/life-spheres").bodyValue(req1).exchange().expectStatus().isOk();

        var req2 = new LifeSphereController.CreateLifeSphereRequest();
        req2.setName("Сфера Б");
        LifeSphereController.LifeSphereResponse area2 = authed.post()
                .uri("/api/v1/life-spheres")
                .bodyValue(req2)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LifeSphereController.LifeSphereResponse.class)
                .returnResult()
                .getResponseBody();

        var updateReq = new LifeSphereController.UpdateLifeSphereRequest();
        updateReq.setName("Сфера А"); // duplicate
        authed.put().uri("/api/v1/life-spheres/{id}", area2.getId()).bodyValue(updateReq).exchange().expectStatus().isBadRequest();
    }

    @Test
    void delete_life_sphere_removes_it() {
        WebTestClient authed = authedAdminClient();

        var createReq = new LifeSphereController.CreateLifeSphereRequest();
        createReq.setName("Для удаления");

        LifeSphereController.LifeSphereResponse created = authed.post()
                .uri("/api/v1/life-spheres")
                .bodyValue(createReq)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LifeSphereController.LifeSphereResponse.class)
                .returnResult()
                .getResponseBody();

        authed.delete().uri("/api/v1/life-spheres/{id}", created.getId()).exchange().expectStatus().isNoContent();

        authed.get().uri("/api/v1/life-spheres")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(LifeSphereController.LifeSphereResponse.class)
                .hasSize(9); // back to seeded only
    }

    @Test
    void archive_life_sphere_toggles_archived() {
        WebTestClient authed = authedAdminClient();

        var createReq = new LifeSphereController.CreateLifeSphereRequest();
        createReq.setName("Архивируемая");

        LifeSphereController.LifeSphereResponse created = authed.post()
                .uri("/api/v1/life-spheres")
                .bodyValue(createReq)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LifeSphereController.LifeSphereResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created.isArchived()).isFalse();

        // Archive
        LifeSphereController.LifeSphereResponse archived = authed.put()
                .uri("/api/v1/life-spheres/{id}/archive", created.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(LifeSphereController.LifeSphereResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(archived.isArchived()).isTrue();

        // Unarchive
        LifeSphereController.LifeSphereResponse unarchived = authed.put()
                .uri("/api/v1/life-spheres/{id}/archive", created.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(LifeSphereController.LifeSphereResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(unarchived.isArchived()).isFalse();
    }

    @Test
    void move_life_sphere_reorders() {
        WebTestClient authed = authedAdminClient();

        var req1 = new LifeSphereController.CreateLifeSphereRequest();
        req1.setName("Сфера А");
        LifeSphereController.LifeSphereResponse a1 = authed.post().uri("/api/v1/life-spheres").bodyValue(req1).exchange().expectStatus().isOk().expectBody(LifeSphereController.LifeSphereResponse.class).returnResult().getResponseBody();

        var req2 = new LifeSphereController.CreateLifeSphereRequest();
        req2.setName("Сфера Б");
        LifeSphereController.LifeSphereResponse a2 = authed.post().uri("/api/v1/life-spheres").bodyValue(req2).exchange().expectStatus().isOk().expectBody(LifeSphereController.LifeSphereResponse.class).returnResult().getResponseBody();

        var req3 = new LifeSphereController.CreateLifeSphereRequest();
        req3.setName("Сфера В");
        LifeSphereController.LifeSphereResponse a3 = authed.post().uri("/api/v1/life-spheres").bodyValue(req3).exchange().expectStatus().isOk().expectBody(LifeSphereController.LifeSphereResponse.class).returnResult().getResponseBody();

        // Move "Сфера В" (index 11) to index 0 (before seeded)
        var moveReq = new LifeSphereController.MoveLifeSphereRequest();
        moveReq.setNewIndex(0);
        authed.put().uri("/api/v1/life-spheres/{id}/move", a3.getId()).bodyValue(moveReq).exchange().expectStatus().isOk();

        List<LifeSphereController.LifeSphereResponse> all = authed.get()
                .uri("/api/v1/life-spheres")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(LifeSphereController.LifeSphereResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(all.get(0).getName()).isEqualTo("Сфера В");
        assertThat(all.get(0).getSortOrder()).isEqualTo(0);
        assertThat(all.get(1).getName()).isEqualTo("Здоровье"); // shifted
        assertThat(all.get(1).getSortOrder()).isEqualTo(1);
    }

    @Test
    void isolation_between_users() {
        // Create second user
        User user2 = new User();
        user2.setUsername("user2");
        user2.setPasswordHash(passwordEncoder.encode("admin"));
        user2.setTimezone("Europe/Moscow");
        user2.setNightStart(java.time.LocalTime.of(23, 0));
        user2.setNightEnd(java.time.LocalTime.of(7, 0));
        user2.setHourAccountingMode("PRIMARY_ONLY");
        userRepository.save(user2);

        // Seed life spheres for user2
        seedLifeSpheres(user2);

        String user2Token = login("user2", "admin");
        WebTestClient user2Client = webTestClient.mutate()
                .defaultHeader("Authorization", "Bearer " + user2Token)
                .build();

        // user2 should see seeded spheres (9)
        user2Client.get().uri("/api/v1/life-spheres").exchange().expectStatus().isOk().expectBodyList(LifeSphereController.LifeSphereResponse.class).hasSize(9);

        // user2 creates an area
        var req = new LifeSphereController.CreateLifeSphereRequest();
        req.setName("Работа user2");
        user2Client.post().uri("/api/v1/life-spheres").bodyValue(req).exchange().expectStatus().isOk();

        // user2 sees 10 spheres (9 seeded + 1 own)
        user2Client.get().uri("/api/v1/life-spheres").exchange().expectStatus().isOk().expectBodyList(LifeSphereController.LifeSphereResponse.class).hasSize(10);

        // admin should see only their own seeded 9
        WebTestClient adminClient = authedAdminClient();
        adminClient.get().uri("/api/v1/life-spheres").exchange().expectStatus().isOk().expectBodyList(LifeSphereController.LifeSphereResponse.class).hasSize(9);
    }

    @Test
    void unauthenticated_access_rejected() {
        webTestClient.get().uri("/api/v1/life-spheres").exchange().expectStatus().isForbidden();
        webTestClient.post().uri("/api/v1/life-spheres").bodyValue(new LifeSphereController.CreateLifeSphereRequest()).exchange().expectStatus().isForbidden();
        webTestClient.put().uri("/api/v1/life-spheres/1").bodyValue(new LifeSphereController.UpdateLifeSphereRequest()).exchange().expectStatus().isForbidden();
        webTestClient.delete().uri("/api/v1/life-spheres/1").exchange().expectStatus().isForbidden();
    }
}
