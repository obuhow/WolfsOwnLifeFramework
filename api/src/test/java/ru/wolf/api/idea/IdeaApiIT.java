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
package ru.wolf.api.idea;

import ru.wolf.api.lifesphere.dto.*;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.lifearea.LifeArea;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.lifesphere.LifeSphere;
import ru.wolf.api.lifesphere.LifeSphereRepository;
import ru.wolf.api.lifesphere.Synergy;
import ru.wolf.api.lifesphere.SynergyController;
import ru.wolf.api.lifesphere.SynergyRepository;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.idea.dto.CreateIdeaRequest;
import ru.wolf.api.idea.dto.IdeaResponse;
import ru.wolf.api.idea.dto.PromoteIdeaRequest;
import ru.wolf.api.idea.dto.PromoteResponse;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.user.UserRepository;

import java.util.List;

class IdeaApiIT extends ApiIntegrationTest {

    @Autowired
    IdeaRepository ideaRepository;

    @Autowired
    SynergyRepository synergyRepository;

    @Autowired
    ProjectRepository projectRepository;

    @Autowired
    LifeAreaRepository lifeAreaRepository;

    @Autowired
    LifeSphereRepository lifeSphereRepository;

    @Autowired
    UserRepository userRepository;

    private LifeArea lifeArea;
    private LifeSphere health;
    private LifeSphere money;

    @BeforeEach
    void cleanup() {
        synergyRepository.deleteAll();
        ideaRepository.deleteAll();
        projectRepository.deleteAll();
        lifeAreaRepository.deleteAll();
        lifeSphereRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(user -> !"admin".equals(user.getUsername()))
                .forEach(userRepository::delete);

        var admin = userRepository.findByUsername("admin").orElseThrow();
        lifeArea = lifeAreaRepository.save(LifeArea.builder()
                .user(admin)
                .name("Работа")
                .sortOrder(0)
                .color("#333333")
                .build());
        health = lifeSphereRepository.save(LifeSphere.builder()
                .user(admin)
                .name("Здоровье")
                .sortOrder(0)
                .color("#EF4444")
                .build());
        money = lifeSphereRepository.save(LifeSphere.builder()
                .user(admin)
                .name("Ресурсы/деньги")
                .sortOrder(1)
                .color("#10B981")
                .build());
    }

    @Test
    void creates_and_filters_ideas_by_category_and_status() {
        WebTestClient client = authedAdminClient();
        create(client, "Музыкальный ролик", Idea.Category.MUSIC, Idea.Status.BANK);
        create(client, "Сервис", Idea.Category.BUSINESS, Idea.Status.ARCHIVED);

        List<IdeaResponse> music = client.get()
                .uri(uri -> uri.path("/api/v1/ideas")
                        .queryParam("category", "MUSIC")
                        .queryParam("status", "BANK")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(IdeaResponse.class)
                .returnResult().getResponseBody();

        assertThat(music).hasSize(1);
        assertThat(music.get(0).title()).isEqualTo("Музыкальный ролик");
        assertThat(music.get(0).category()).isEqualTo(Idea.Category.MUSIC);
    }

    @Test
    void idea_synergy_is_owned_and_read_by_idea_id() {
        WebTestClient client = authedAdminClient();
        IdeaResponse idea = create(client, "Идея", Idea.Category.PERSONAL, Idea.Status.BANK);

        var request = new CreateSynergyRequest(health.getId(), null, idea.id(), null, Synergy.Impact.POSITIVE);

        client.post().uri("/api/v1/synergies").bodyValue(request)
                .exchange().expectStatus().isOk();

        List<SynergyResponse> synergies = client.get()
                .uri(uri -> uri.path("/api/v1/synergies").queryParam("ideaId", idea.id()).build())
                .exchange().expectStatus().isOk()
                .expectBodyList(SynergyResponse.class)
                .returnResult().getResponseBody();

        assertThat(synergies).hasSize(1);
        assertThat(synergies.get(0).ideaId()).isEqualTo(idea.id());
        assertThat(synergies.get(0).sphereName()).isEqualTo("Здоровье");
    }

    @Test
    void promote_copies_description_and_synergy_and_rejects_second_promotion() {
        WebTestClient client = authedAdminClient();
        IdeaResponse idea = create(client, "Instagram муз. ролики", Idea.Category.MUSIC, Idea.Status.BANK);

        createIdeaSynergy(client, idea.id(), health.getId(), Synergy.Impact.POSITIVE);
        createIdeaSynergy(client, idea.id(), money.getId(), Synergy.Impact.NEGATIVE);

        var promote = new PromoteIdeaRequest(lifeArea.getId());
        PromoteResponse promoted = client.post()
                .uri("/api/v1/ideas/{id}/promote", idea.id())
                .bodyValue(promote)
                .exchange().expectStatus().isOk()
                .expectBody(PromoteResponse.class)
                .returnResult().getResponseBody();

        assertThat(promoted.projectId()).isNotNull();
        assertThat(promoted.ideaId()).isEqualTo(idea.id());
        assertThat(projectRepository.findById(promoted.projectId())).get()
                .extracting(project -> project.getTitle(), project -> project.getDescription())
                .containsExactly("Instagram муз. ролики", "Описание");

        List<Synergy> copied = synergyRepository.findByUserAndProjectWithSphere(
                userRepository.findByUsername("admin").orElseThrow(),
                projectRepository.findById(promoted.projectId()).orElseThrow());
        assertThat(copied).hasSize(2);
        assertThat(copied).extracting(Synergy::getImpact)
                .containsExactlyInAnyOrder(Synergy.Impact.POSITIVE, Synergy.Impact.NEGATIVE);
        assertThat(copied).extracting(synergy -> synergy.getSphere().getId())
                .containsExactlyInAnyOrder(health.getId(), money.getId());

        client.post().uri("/api/v1/ideas/{id}/promote", idea.id())
                .bodyValue(promote).exchange().expectStatus().isEqualTo(409);

        IdeaResponse updated = client.get()
                .uri("/api/v1/ideas/{id}", idea.id())
                .exchange().expectStatus().isOk()
                .expectBody(IdeaResponse.class)
                .returnResult().getResponseBody();
        assertThat(updated.status()).isEqualTo(Idea.Status.IN_WORK);
        assertThat(updated.promotedProjectId()).isEqualTo(promoted.projectId());
    }

    @Test
    void archived_idea_cannot_be_promoted() {
        WebTestClient client = authedAdminClient();
        IdeaResponse idea = create(client, "Архивная идея", Idea.Category.PERSONAL, Idea.Status.ARCHIVED);

        client.post().uri("/api/v1/ideas/{id}/promote", idea.id())
                .bodyValue(new PromoteIdeaRequest(lifeArea.getId()))
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void synergy_get_rejects_two_target_ids() {
        WebTestClient client = authedAdminClient();
        IdeaResponse idea = create(client, "Идея", Idea.Category.PERSONAL, Idea.Status.BANK);

        client.get().uri(uri -> uri.path("/api/v1/synergies")
                        .queryParam("ideaId", idea.id())
                        .queryParam("projectId", 123L)
                        .build())
                .exchange().expectStatus().isBadRequest();
    }

    private IdeaResponse create(
            WebTestClient client, String title, Idea.Category category, Idea.Status status) {
        var request = new CreateIdeaRequest(title, "Описание", category, status);
        return client.post().uri("/api/v1/ideas").bodyValue(request)
                .exchange().expectStatus().isOk()
                .expectBody(IdeaResponse.class)
                .returnResult().getResponseBody();
    }

    private void createIdeaSynergy(
            WebTestClient client, Long ideaId, Long sphereId, Synergy.Impact impact) {
        var request = new CreateSynergyRequest(sphereId, null, ideaId, null, impact);
        client.post().uri("/api/v1/synergies").bodyValue(request)
                .exchange().expectStatus().isOk();
    }
}
