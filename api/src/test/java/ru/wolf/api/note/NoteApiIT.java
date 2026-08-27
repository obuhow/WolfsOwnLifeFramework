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
package ru.wolf.api.note;

import ru.wolf.api.delo.dto.*;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.lifearea.dto.*;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.note.dto.NoteRequest;
import ru.wolf.api.note.dto.NoteResponse;
import ru.wolf.api.project.dto.*;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.support.ApiIntegrationTest;

import java.util.List;

class NoteApiIT extends ApiIntegrationTest {

    @Autowired
    NoteRepository noteRepository;

    @Autowired
    DeloProjectRepository deloProjectRepository;

    @Autowired
    DeloRepository deloRepository;

    @Autowired
    ProjectRepository projectRepository;

    @Autowired
    LifeAreaRepository lifeAreaRepository;

    @BeforeEach
    void cleanup() {
        noteRepository.deleteAll();
        deloProjectRepository.deleteAll();
        deloRepository.deleteAll();
        projectRepository.deleteAll();
        lifeAreaRepository.deleteAll();
    }

    @Test
    void crud_notes_for_project_and_delo_with_filters() {
        WebTestClient client = authedAdminClient();
        Long areaId = createLifeArea(client, "Работа");
        ProjectResponse project = createProject(client, areaId, "WOLF");
        DeloResponse delo = createDelo(client, "Spring Security");

        NoteRequest projectRequest = new NoteRequest(project.id(), null, null, "Решение по Spring Security и JWT", List.of("security", "решение"));
        NoteResponse projectNote = createNote(client, projectRequest);

        NoteRequest agentRequest = new NoteRequest(null, delo.id(), Note.Author.AGENT, "Агент нашёл справку по фильтрам", List.of("research"));
        NoteResponse agentNote = createNote(client, agentRequest);

        assertThat(projectNote.projectId()).isEqualTo(project.id());
        assertThat(projectNote.deloId()).isNull();
        assertThat(agentNote.deloId()).isEqualTo(delo.id());
        assertThat(agentNote.author()).isEqualTo(Note.Author.AGENT);

        client.get().uri(uri -> uri.path("/api/v1/notes")
                        .queryParam("projectId", project.id()).build())
                .exchange().expectStatus().isOk()
                .expectBodyList(NoteResponse.class).hasSize(1);

        client.get().uri(uri -> uri.path("/api/v1/notes")
                        .queryParam("author", "AGENT").queryParam("tag", "research").build())
                .exchange().expectStatus().isOk()
                .expectBodyList(NoteResponse.class).hasSize(1);

        List<NoteResponse> search = client.get()
                .uri(uri -> uri.path("/api/v1/notes").queryParam("q", "Spring Security").build())
                .exchange().expectStatus().isOk()
                .expectBodyList(NoteResponse.class).returnResult().getResponseBody();
        assertThat(search).extracting(NoteResponse::id).contains(projectNote.id());

        var updatedRequest = new NoteRequest(project.id(), null, null, "Обновлённое решение", List.of("updated"));
        client.put().uri("/api/v1/notes/{id}", projectNote.id()).bodyValue(updatedRequest)
                .exchange().expectStatus().isOk();
        client.get().uri("/api/v1/notes/{id}", projectNote.id()).exchange()
                .expectStatus().isOk().expectBody(NoteResponse.class)
                .value(note -> assertThat(note.body()).isEqualTo("Обновлённое решение"));

        client.delete().uri("/api/v1/notes/{id}", agentNote.id()).exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void editing_agent_note_preserves_attribution() {
        WebTestClient client = authedAdminClient();
        DeloResponse delo = createDelo(client, "Агентская заметка");

        NoteRequest create = new NoteRequest(null, delo.id(), Note.Author.AGENT, "Исходная заметка агента", null);
        NoteResponse note = createNote(client, create);

        NoteRequest update = new NoteRequest(null, delo.id(), null, "Обновлённая заметка агента", null);
        NoteResponse updated = client.put()
                .uri("/api/v1/notes/{id}", note.id())
                .bodyValue(update)
                .exchange().expectStatus().isOk()
                .expectBody(NoteResponse.class)
                .returnResult().getResponseBody();

        assertThat(updated.author()).isEqualTo(Note.Author.AGENT);
        assertThat(updated.body()).isEqualTo("Обновлённая заметка агента");
    }

    @Test
    void note_requires_exactly_one_parent() {
        WebTestClient client = authedAdminClient();
        Long areaId = createLifeArea(client, "Работа");
        ProjectResponse project = createProject(client, areaId, "WOLF");
        DeloResponse delo = createDelo(client, "Рутина");

        NoteRequest empty = new NoteRequest(null, null, null, "Без привязки", null);
        client.post().uri("/api/v1/notes").bodyValue(empty).exchange()
                .expectStatus().isBadRequest();

        NoteRequest both = new NoteRequest(project.id(), delo.id(), null, "Две привязки", null);
        client.post().uri("/api/v1/notes").bodyValue(both).exchange()
                .expectStatus().isBadRequest();
    }

    private NoteResponse createNote(WebTestClient client, NoteRequest request) {
        return client.post().uri("/api/v1/notes").bodyValue(request).exchange()
                .expectStatus().isOk().expectBody(NoteResponse.class)
                .returnResult().getResponseBody();
    }

    private Long createLifeArea(WebTestClient client, String name) {
        var request = new CreateLifeAreaRequest(name, "#3d5a4a");
        return client.post().uri("/api/v1/life-areas").bodyValue(request).exchange()
                .expectStatus().isOk().expectBody(LifeAreaResponse.class)
                .returnResult().getResponseBody().id();
    }

    private ProjectResponse createProject(WebTestClient client, Long areaId, String title) {
        var request = new CreateProjectRequest(areaId, title);
        return client.post().uri("/api/v1/projects").bodyValue(request).exchange()
                .expectStatus().isOk().expectBody(ProjectResponse.class)
                .returnResult().getResponseBody();
    }

    private DeloResponse createDelo(WebTestClient client, String title) {
        var request = new CreateDeloRequest(title, null, null, null, null);
        return client.post().uri("/api/v1/delos").bodyValue(request).exchange()
                .expectStatus().isOk().expectBody(DeloResponse.class)
                .returnResult().getResponseBody();
    }
}
