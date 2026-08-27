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
import ru.wolf.api.lifearea.dto.*;
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
                .expectBodyList(LifeAreaResponse.class)
                .hasSize(0);
    }

    @Test
    void create_life_area_persists_and_returns() {
        WebTestClient authed = authedAdminClient();

        var request = new CreateLifeAreaRequest("Работа", "#3d5a4a");

        LifeAreaResponse created = authed.post()
                .uri("/api/v1/life-areas")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LifeAreaResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("Работа");
        assertThat(created.color()).isEqualTo("#3d5a4a");
        assertThat(created.sortOrder()).isEqualTo(0);
    }

    @Test
    void create_multiple_life_areas_gets_sequential_sort_order() {
        WebTestClient authed = authedAdminClient();

        var req1 = new CreateLifeAreaRequest("Работа", "#3d5a4a");
        var req2 = new CreateLifeAreaRequest("Здоровье", "#8a4b2f");
        var req3 = new CreateLifeAreaRequest("Семья", "#a89a88");

        authed.post().uri("/api/v1/life-areas").bodyValue(req1).exchange().expectStatus().isOk();
        authed.post().uri("/api/v1/life-areas").bodyValue(req2).exchange().expectStatus().isOk();
        authed.post().uri("/api/v1/life-areas").bodyValue(req3).exchange().expectStatus().isOk();

        List<LifeAreaResponse> all = authed.get()
                .uri("/api/v1/life-areas")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(LifeAreaResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(all).hasSize(3);
        assertThat(all.get(0).name()).isEqualTo("Работа");
        assertThat(all.get(0).sortOrder()).isEqualTo(0);
        assertThat(all.get(1).name()).isEqualTo("Здоровье");
        assertThat(all.get(1).sortOrder()).isEqualTo(1);
        assertThat(all.get(2).name()).isEqualTo("Семья");
        assertThat(all.get(2).sortOrder()).isEqualTo(2);
    }

    @Test
    void duplicate_name_rejected() {
        WebTestClient authed = authedAdminClient();

        var request = new CreateLifeAreaRequest("Работа", null);

        authed.post().uri("/api/v1/life-areas").bodyValue(request).exchange().expectStatus().isOk();

        authed.post().uri("/api/v1/life-areas").bodyValue(request).exchange().expectStatus().isBadRequest();
    }

    @Test
    void update_life_area_changes_name_and_color() {
        WebTestClient authed = authedAdminClient();

        var createReq = new CreateLifeAreaRequest("Работа", "#3d5a4a");

        LifeAreaResponse created = authed.post()
                .uri("/api/v1/life-areas")
                .bodyValue(createReq)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LifeAreaResponse.class)
                .returnResult()
                .getResponseBody();

        var updateReq = new UpdateLifeAreaRequest("Карьера", "#8a4b2f");

        LifeAreaResponse updated = authed.put()
                .uri("/api/v1/life-areas/{id}", created.id())
                .bodyValue(updateReq)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LifeAreaResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(updated.name()).isEqualTo("Карьера");
        assertThat(updated.color()).isEqualTo("#8a4b2f");
        assertThat(updated.id()).isEqualTo(created.id());
    }

    @Test
    void update_duplicate_name_rejected() {
        WebTestClient authed = authedAdminClient();

        var req1 = new CreateLifeAreaRequest("Работа", null);
        authed.post().uri("/api/v1/life-areas").bodyValue(req1).exchange().expectStatus().isOk();

        var req2 = new CreateLifeAreaRequest("Здоровье", null);
        LifeAreaResponse area2 = authed.post()
                .uri("/api/v1/life-areas")
                .bodyValue(req2)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LifeAreaResponse.class)
                .returnResult()
                .getResponseBody();

        var updateReq = new UpdateLifeAreaRequest("Работа", null); // duplicate
        authed.put().uri("/api/v1/life-areas/{id}", area2.id()).bodyValue(updateReq).exchange().expectStatus().isBadRequest();
    }

    @Test
    void delete_life_area_removes_it() {
        WebTestClient authed = authedAdminClient();

        var createReq = new CreateLifeAreaRequest("Работа", null);

        LifeAreaResponse created = authed.post()
                .uri("/api/v1/life-areas")
                .bodyValue(createReq)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LifeAreaResponse.class)
                .returnResult()
                .getResponseBody();

        authed.delete().uri("/api/v1/life-areas/{id}", created.id()).exchange().expectStatus().isNoContent();

        authed.get().uri("/api/v1/life-areas").exchange().expectStatus().isOk().expectBodyList(LifeAreaResponse.class).hasSize(0);
    }

    @Test
    void move_life_area_reorders() {
        WebTestClient authed = authedAdminClient();

        var req1 = new CreateLifeAreaRequest("Работа", null);
        LifeAreaResponse a1 = authed.post().uri("/api/v1/life-areas").bodyValue(req1).exchange().expectStatus().isOk().expectBody(LifeAreaResponse.class).returnResult().getResponseBody();

        var req2 = new CreateLifeAreaRequest("Здоровье", null);
        LifeAreaResponse a2 = authed.post().uri("/api/v1/life-areas").bodyValue(req2).exchange().expectStatus().isOk().expectBody(LifeAreaResponse.class).returnResult().getResponseBody();

        var req3 = new CreateLifeAreaRequest("Семья", null);
        LifeAreaResponse a3 = authed.post().uri("/api/v1/life-areas").bodyValue(req3).exchange().expectStatus().isOk().expectBody(LifeAreaResponse.class).returnResult().getResponseBody();

        // Move "Семья" (index 2) to index 0
        var moveReq = new MoveLifeAreaRequest(0);
        authed.put().uri("/api/v1/life-areas/{id}/move", a3.id()).bodyValue(moveReq).exchange().expectStatus().isOk();

        List<LifeAreaResponse> all = authed.get()
                .uri("/api/v1/life-areas")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(LifeAreaResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(all.get(0).name()).isEqualTo("Семья");
        assertThat(all.get(0).sortOrder()).isEqualTo(0);
        assertThat(all.get(1).name()).isEqualTo("Работа");
        assertThat(all.get(1).sortOrder()).isEqualTo(1);
        assertThat(all.get(2).name()).isEqualTo("Здоровье");
        assertThat(all.get(2).sortOrder()).isEqualTo(2);
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
        var req = new CreateLifeAreaRequest("Работа user2", null);
        user2Client.post().uri("/api/v1/life-areas").bodyValue(req).exchange().expectStatus().isOk();

        // admin should not see user2's area
        WebTestClient adminClient = authedAdminClient();
        adminClient.get().uri("/api/v1/life-areas").exchange().expectStatus().isOk().expectBodyList(LifeAreaResponse.class).hasSize(0);

        // user2 sees their area
        user2Client.get().uri("/api/v1/life-areas").exchange().expectStatus().isOk().expectBodyList(LifeAreaResponse.class).hasSize(1);
    }

    @Test
    void unauthenticated_access_rejected() {
        webTestClient.get().uri("/api/v1/life-areas").exchange().expectStatus().isForbidden();
        webTestClient.post().uri("/api/v1/life-areas").bodyValue(new CreateLifeAreaRequest(null, null)).exchange().expectStatus().isForbidden();
        webTestClient.put().uri("/api/v1/life-areas/1").bodyValue(new UpdateLifeAreaRequest(null, null)).exchange().expectStatus().isForbidden();
        webTestClient.delete().uri("/api/v1/life-areas/1").exchange().expectStatus().isForbidden();
    }
}
