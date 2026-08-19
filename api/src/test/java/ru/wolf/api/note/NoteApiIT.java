package ru.wolf.api.note;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.delo.DeloController;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.lifearea.LifeAreaController;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.project.ProjectController;
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
        ProjectController.ProjectResponse project = createProject(client, areaId, "WOLF");
        DeloController.DeloResponse delo = createDelo(client, "Spring Security");

        NoteController.NoteRequest projectRequest = new NoteController.NoteRequest();
        projectRequest.setProjectId(project.getId());
        projectRequest.setBody("Решение по Spring Security и JWT");
        projectRequest.setTags(List.of("security", "решение"));
        NoteController.NoteResponse projectNote = createNote(client, projectRequest);

        NoteController.NoteRequest agentRequest = new NoteController.NoteRequest();
        agentRequest.setDeloId(delo.getId());
        agentRequest.setAuthor(Note.Author.AGENT);
        agentRequest.setBody("Агент нашёл справку по фильтрам");
        agentRequest.setTags(List.of("research"));
        NoteController.NoteResponse agentNote = createNote(client, agentRequest);

        assertThat(projectNote.getProjectId()).isEqualTo(project.getId());
        assertThat(projectNote.getDeloId()).isNull();
        assertThat(agentNote.getDeloId()).isEqualTo(delo.getId());
        assertThat(agentNote.getAuthor()).isEqualTo(Note.Author.AGENT);

        client.get().uri(uri -> uri.path("/api/v1/notes")
                        .queryParam("projectId", project.getId()).build())
                .exchange().expectStatus().isOk()
                .expectBodyList(NoteController.NoteResponse.class).hasSize(1);

        client.get().uri(uri -> uri.path("/api/v1/notes")
                        .queryParam("author", "AGENT").queryParam("tag", "research").build())
                .exchange().expectStatus().isOk()
                .expectBodyList(NoteController.NoteResponse.class).hasSize(1);

        List<NoteController.NoteResponse> search = client.get()
                .uri(uri -> uri.path("/api/v1/notes").queryParam("q", "Spring Security").build())
                .exchange().expectStatus().isOk()
                .expectBodyList(NoteController.NoteResponse.class).returnResult().getResponseBody();
        assertThat(search).extracting(NoteController.NoteResponse::getId).contains(projectNote.getId());

        projectRequest.setBody("Обновлённое решение");
        projectRequest.setTags(List.of("updated"));
        client.put().uri("/api/v1/notes/{id}", projectNote.getId()).bodyValue(projectRequest)
                .exchange().expectStatus().isOk();
        client.get().uri("/api/v1/notes/{id}", projectNote.getId()).exchange()
                .expectStatus().isOk().expectBody(NoteController.NoteResponse.class)
                .value(note -> assertThat(note.getBody()).isEqualTo("Обновлённое решение"));

        client.delete().uri("/api/v1/notes/{id}", agentNote.getId()).exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void note_requires_exactly_one_parent() {
        WebTestClient client = authedAdminClient();
        Long areaId = createLifeArea(client, "Работа");
        ProjectController.ProjectResponse project = createProject(client, areaId, "WOLF");
        DeloController.DeloResponse delo = createDelo(client, "Рутина");

        NoteController.NoteRequest empty = new NoteController.NoteRequest();
        empty.setBody("Без привязки");
        client.post().uri("/api/v1/notes").bodyValue(empty).exchange()
                .expectStatus().isBadRequest();

        NoteController.NoteRequest both = new NoteController.NoteRequest();
        both.setProjectId(project.getId());
        both.setDeloId(delo.getId());
        both.setBody("Две привязки");
        client.post().uri("/api/v1/notes").bodyValue(both).exchange()
                .expectStatus().isBadRequest();
    }

    private NoteController.NoteResponse createNote(WebTestClient client, NoteController.NoteRequest request) {
        return client.post().uri("/api/v1/notes").bodyValue(request).exchange()
                .expectStatus().isOk().expectBody(NoteController.NoteResponse.class)
                .returnResult().getResponseBody();
    }

    private Long createLifeArea(WebTestClient client, String name) {
        var request = new LifeAreaController.CreateLifeAreaRequest();
        request.setName(name);
        request.setColor("#3d5a4a");
        return client.post().uri("/api/v1/life-areas").bodyValue(request).exchange()
                .expectStatus().isOk().expectBody(LifeAreaController.LifeAreaResponse.class)
                .returnResult().getResponseBody().getId();
    }

    private ProjectController.ProjectResponse createProject(WebTestClient client, Long areaId, String title) {
        var request = new ProjectController.CreateProjectRequest();
        request.setLifeAreaId(areaId);
        request.setTitle(title);
        return client.post().uri("/api/v1/projects").bodyValue(request).exchange()
                .expectStatus().isOk().expectBody(ProjectController.ProjectResponse.class)
                .returnResult().getResponseBody();
    }

    private DeloController.DeloResponse createDelo(WebTestClient client, String title) {
        var request = new DeloController.CreateDeloRequest();
        request.setTitle(title);
        return client.post().uri("/api/v1/delos").bodyValue(request).exchange()
                .expectStatus().isOk().expectBody(DeloController.DeloResponse.class)
                .returnResult().getResponseBody();
    }
}
