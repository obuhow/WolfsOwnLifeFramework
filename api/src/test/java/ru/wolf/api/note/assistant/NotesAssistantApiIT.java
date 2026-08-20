package ru.wolf.api.note.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import ru.wolf.api.delo.DeloController;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.lifearea.LifeAreaController;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.note.Note;
import ru.wolf.api.note.NoteController;
import ru.wolf.api.note.NoteRepository;
import ru.wolf.api.project.ProjectController;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.support.ApiIntegrationTest;

@ActiveProfiles("test")
@TestPropertySource(properties = "wolf.llm.enabled=true")
class NotesAssistantApiIT extends ApiIntegrationTest {

    @Autowired
    private FakeNotesAssistant fakeNotesAssistant;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private DeloProjectRepository deloProjectRepository;

    @Autowired
    private DeloRepository deloRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private LifeAreaRepository lifeAreaRepository;

    @BeforeEach
    void cleanup() {
        noteRepository.deleteAll();
        deloProjectRepository.deleteAll();
        deloRepository.deleteAll();
        projectRepository.deleteAll();
        lifeAreaRepository.deleteAll();
        fakeNotesAssistant.reset();
    }

    @Test
    void uploading_audio_creates_note_with_fake_transcription_and_attachment() {
        WebTestClient client = authedAdminClient();
        Long projectId = createProject(client, "WOLF").getId();

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new NamedByteArrayResource("voice memo".getBytes(StandardCharsets.UTF_8), "memo.webm"));
        parts.add("projectId", projectId.toString());
        parts.add("tags", "voice");

        client.post().uri("/api/v1/notes/audio")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(parts)
                .exchange()
                .expectStatus().isOk()
                .expectBody(NoteController.NoteResponse.class)
                .value(note -> {
                    assertThat(note.getBody()).isEqualTo("Тестовая транскрипция");
                    assertThat(note.getProjectId()).isEqualTo(projectId);
                    assertThat(note.getAudioRef()).isNotBlank();
                    assertThat(note.getTags()).containsExactly("voice");
                });
    }

    @Test
    void resume_summarizes_latest_project_notes_through_fake() {
        WebTestClient client = authedAdminClient();
        Long projectId = createProject(client, "WOLF").getId();
        createNote(client, projectId, "Решили оставить JWT фильтр");
        createNote(client, projectId, "Остановился на проверке Spring Security");
        createNote(client, projectId, "Следом нужно добавить интеграционный тест");
        fakeNotesAssistant.setSummaryResponse("Остановился на Spring Security: JWT фильтр и интеграционный тест.");

        client.get().uri(uri -> uri.path("/api/v1/projects/{id}/resume").queryParam("limit", 10).build(projectId))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProjectResumeController.ResumeResponse.class)
                .value(resume -> {
                    assertThat(resume.projectId()).isEqualTo(projectId);
                    assertThat(resume.noteIds()).hasSize(3);
                    assertThat(resume.summary()).contains("Spring Security", "JWT", "интеграционный тест");
                });
    }


    private ProjectController.ProjectResponse createProject(WebTestClient client, String title) {
        var areaRequest = new LifeAreaController.CreateLifeAreaRequest();
        areaRequest.setName("Работа");
        areaRequest.setColor("#3d5a4a");
        Long areaId = client.post().uri("/api/v1/life-areas").bodyValue(areaRequest).exchange()
                .expectStatus().isOk().expectBody(LifeAreaController.LifeAreaResponse.class)
                .returnResult().getResponseBody().getId();

        var projectRequest = new ProjectController.CreateProjectRequest();
        projectRequest.setLifeAreaId(areaId);
        projectRequest.setTitle(title);
        return client.post().uri("/api/v1/projects").bodyValue(projectRequest).exchange()
                .expectStatus().isOk().expectBody(ProjectController.ProjectResponse.class)
                .returnResult().getResponseBody();
    }

    private void createNote(WebTestClient client, Long projectId, String body) {
        var request = new NoteController.NoteRequest();
        request.setProjectId(projectId);
        request.setBody(body);
        client.post().uri("/api/v1/notes").bodyValue(request).exchange()
                .expectStatus().isOk();
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
