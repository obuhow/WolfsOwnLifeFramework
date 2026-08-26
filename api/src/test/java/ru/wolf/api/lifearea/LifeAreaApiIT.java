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
package ru.wolf.api.lifearea;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.util.List;

class LifeAreaApiIT extends ApiIntegrationTest {

    @Autowired
    LifeAreaRepository lifeAreaRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanup() {
        lifeAreaRepository.deleteAll();
        // keep seed admin; remove extra users from previous tests
        userRepository.findAll().stream()
                .filter(u -> !"admin".equals(u.getUsername()))
                .forEach(userRepository::delete);
    }

    @Test
    void get_life_areas_returns_empty_for_new_user() {
        WebTestClient authed = authedAdminClient();

        authed.get()
                .uri("/api/v1/life-areas")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(LifeAreaController.LifeAreaResponse.class)
                .hasSize(0);
    }

    @Test
    void create_life_area_persists_and_returns() {
        WebTestClient authed = authedAdminClient();

        var request = new LifeAreaController.CreateLifeAreaRequest();
        request.setName("Работа");
        request.setColor("#3d5a4a");

        LifeAreaController.LifeAreaResponse created = authed.post()
                .uri("/api/v1/life-areas")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LifeAreaController.LifeAreaResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("Работа");
        assertThat(created.getColor()).isEqualTo("#3d5a4a");
        assertThat(created.getSortOrder()).isEqualTo(0);
    }

    @Test
    void create_multiple_life_areas_gets_sequential_sort_order() {
        WebTestClient authed = authedAdminClient();

        var req1 = new LifeAreaController.CreateLifeAreaRequest();
        req1.setName("Работа");
        req1.setColor("#3d5a4a");

        var req2 = new LifeAreaController.CreateLifeAreaRequest();
        req2.setName("Здоровье");
        req2.setColor("#8a4b2f");

        var req3 = new LifeAreaController.CreateLifeAreaRequest();
        req3.setName("Семья");
        req3.setColor("#a89a88");

        authed.post().uri("/api/v1/life-areas").bodyValue(req1).exchange().expectStatus().isOk();
        authed.post().uri("/api/v1/life-areas").bodyValue(req2).exchange().expectStatus().isOk();
        authed.post().uri("/api/v1/life-areas").bodyValue(req3).exchange().expectStatus().isOk();

        List<LifeAreaController.LifeAreaResponse> all = authed.get()
                .uri("/api/v1/life-areas")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(LifeAreaController.LifeAreaResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(all).hasSize(3);
        assertThat(all.get(0).getName()).isEqualTo("Работа");
        assertThat(all.get(0).getSortOrder()).isEqualTo(0);
        assertThat(all.get(1).getName()).isEqualTo("Здоровье");
        assertThat(all.get(1).getSortOrder()).isEqualTo(1);
        assertThat(all.get(2).getName()).isEqualTo("Семья");
        assertThat(all.get(2).getSortOrder()).isEqualTo(2);
    }

    @Test
    void duplicate_name_rejected() {
        WebTestClient authed = authedAdminClient();

        var request = new LifeAreaController.CreateLifeAreaRequest();
        request.setName("Работа");

        authed.post().uri("/api/v1/life-areas").bodyValue(request).exchange().expectStatus().isOk();

        authed.post().uri("/api/v1/life-areas").bodyValue(request).exchange().expectStatus().isBadRequest();
    }

    @Test
    void update_life_area_changes_name_and_color() {
        WebTestClient authed = authedAdminClient();

        var createReq = new LifeAreaController.CreateLifeAreaRequest();
        createReq.setName("Работа");
        createReq.setColor("#3d5a4a");

        LifeAreaController.LifeAreaResponse created = authed.post()
                .uri("/api/v1/life-areas")
                .bodyValue(createReq)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LifeAreaController.LifeAreaResponse.class)
                .returnResult()
                .getResponseBody();

        var updateReq = new LifeAreaController.UpdateLifeAreaRequest();
        updateReq.setName("Карьера");
        updateReq.setColor("#8a4b2f");

        LifeAreaController.LifeAreaResponse updated = authed.put()
                .uri("/api/v1/life-areas/{id}", created.getId())
                .bodyValue(updateReq)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LifeAreaController.LifeAreaResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(updated.getName()).isEqualTo("Карьера");
        assertThat(updated.getColor()).isEqualTo("#8a4b2f");
        assertThat(updated.getId()).isEqualTo(created.getId());
    }

    @Test
    void update_duplicate_name_rejected() {
        WebTestClient authed = authedAdminClient();

        var req1 = new LifeAreaController.CreateLifeAreaRequest();
        req1.setName("Работа");
        authed.post().uri("/api/v1/life-areas").bodyValue(req1).exchange().expectStatus().isOk();

        var req2 = new LifeAreaController.CreateLifeAreaRequest();
        req2.setName("Здоровье");
        LifeAreaController.LifeAreaResponse area2 = authed.post()
                .uri("/api/v1/life-areas")
                .bodyValue(req2)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LifeAreaController.LifeAreaResponse.class)
                .returnResult()
                .getResponseBody();

        var updateReq = new LifeAreaController.UpdateLifeAreaRequest();
        updateReq.setName("Работа"); // duplicate
        authed.put().uri("/api/v1/life-areas/{id}", area2.getId()).bodyValue(updateReq).exchange().expectStatus().isBadRequest();
    }

    @Test
    void delete_life_area_removes_it() {
        WebTestClient authed = authedAdminClient();

        var createReq = new LifeAreaController.CreateLifeAreaRequest();
        createReq.setName("Работа");

        LifeAreaController.LifeAreaResponse created = authed.post()
                .uri("/api/v1/life-areas")
                .bodyValue(createReq)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LifeAreaController.LifeAreaResponse.class)
                .returnResult()
                .getResponseBody();

        authed.delete().uri("/api/v1/life-areas/{id}", created.getId()).exchange().expectStatus().isNoContent();

        authed.get().uri("/api/v1/life-areas").exchange().expectStatus().isOk().expectBodyList(LifeAreaController.LifeAreaResponse.class).hasSize(0);
    }

    @Test
    void move_life_area_reorders() {
        WebTestClient authed = authedAdminClient();

        var req1 = new LifeAreaController.CreateLifeAreaRequest();
        req1.setName("Работа");
        LifeAreaController.LifeAreaResponse a1 = authed.post().uri("/api/v1/life-areas").bodyValue(req1).exchange().expectStatus().isOk().expectBody(LifeAreaController.LifeAreaResponse.class).returnResult().getResponseBody();

        var req2 = new LifeAreaController.CreateLifeAreaRequest();
        req2.setName("Здоровье");
        LifeAreaController.LifeAreaResponse a2 = authed.post().uri("/api/v1/life-areas").bodyValue(req2).exchange().expectStatus().isOk().expectBody(LifeAreaController.LifeAreaResponse.class).returnResult().getResponseBody();

        var req3 = new LifeAreaController.CreateLifeAreaRequest();
        req3.setName("Семья");
        LifeAreaController.LifeAreaResponse a3 = authed.post().uri("/api/v1/life-areas").bodyValue(req3).exchange().expectStatus().isOk().expectBody(LifeAreaController.LifeAreaResponse.class).returnResult().getResponseBody();

        // Move "Семья" (index 2) to index 0
        var moveReq = new LifeAreaController.MoveLifeAreaRequest();
        moveReq.setNewIndex(0);
        authed.put().uri("/api/v1/life-areas/{id}/move", a3.getId()).bodyValue(moveReq).exchange().expectStatus().isOk();

        List<LifeAreaController.LifeAreaResponse> all = authed.get()
                .uri("/api/v1/life-areas")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(LifeAreaController.LifeAreaResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(all.get(0).getName()).isEqualTo("Семья");
        assertThat(all.get(0).getSortOrder()).isEqualTo(0);
        assertThat(all.get(1).getName()).isEqualTo("Работа");
        assertThat(all.get(1).getSortOrder()).isEqualTo(1);
        assertThat(all.get(2).getName()).isEqualTo("Здоровье");
        assertThat(all.get(2).getSortOrder()).isEqualTo(2);
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

        String user2Token = login("user2", "admin");
        WebTestClient user2Client = webTestClient.mutate()
                .defaultHeader("Authorization", "Bearer " + user2Token)
                .build();

        // user2 creates an area
        var req = new LifeAreaController.CreateLifeAreaRequest();
        req.setName("Работа user2");
        user2Client.post().uri("/api/v1/life-areas").bodyValue(req).exchange().expectStatus().isOk();

        // admin should not see user2's area
        WebTestClient adminClient = authedAdminClient();
        adminClient.get().uri("/api/v1/life-areas").exchange().expectStatus().isOk().expectBodyList(LifeAreaController.LifeAreaResponse.class).hasSize(0);

        // user2 sees their area
        user2Client.get().uri("/api/v1/life-areas").exchange().expectStatus().isOk().expectBodyList(LifeAreaController.LifeAreaResponse.class).hasSize(1);
    }

    @Test
    void unauthenticated_access_rejected() {
        webTestClient.get().uri("/api/v1/life-areas").exchange().expectStatus().isForbidden();
        webTestClient.post().uri("/api/v1/life-areas").bodyValue(new LifeAreaController.CreateLifeAreaRequest()).exchange().expectStatus().isForbidden();
        webTestClient.put().uri("/api/v1/life-areas/1").bodyValue(new LifeAreaController.UpdateLifeAreaRequest()).exchange().expectStatus().isForbidden();
        webTestClient.delete().uri("/api/v1/life-areas/1").exchange().expectStatus().isForbidden();
    }
}