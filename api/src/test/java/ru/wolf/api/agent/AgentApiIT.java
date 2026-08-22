package ru.wolf.api.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.note.Note;
import ru.wolf.api.note.NoteRepository;
import ru.wolf.api.note.assistant.FakeNotesAssistant;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.timeentry.TimeEntryRepository;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
class AgentApiIT extends ApiIntegrationTest {

    @Autowired
    private AgentJob agentJob;

    @Autowired
    private FakeNotesAssistant fakeNotesAssistant;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private AgentRunLogRepository runLogRepository;

    @Autowired
    private DeloProjectRepository deloProjectRepository;

    @Autowired
    private DeloRepository deloRepository;

    @Autowired
    private TimeEntryRepository timeEntryRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private LifeAreaRepository lifeAreaRepository;

    @BeforeEach
    void cleanup() {
        noteRepository.deleteAll();
        runLogRepository.deleteAll();
        timeEntryRepository.deleteAll();
        deloProjectRepository.deleteAll();
        deloRepository.deleteAll();
        projectRepository.deleteAll();
        lifeAreaRepository.deleteAll();
        fakeNotesAssistant.reset();
    }

    @Test
    void manual_run_creates_agent_note_and_is_idempotent_for_the_day() {
        WebTestClient client = authedAdminClient();
        Long projectId = createProject(client);
        fakeNotesAssistant.setSuggestionResponse("Ролики, статья с Хабра и факт по теме проекта");

        client.post().uri("/api/v1/admin/agent/run")
                .exchange().expectStatus().isOk()
                .expectBody(AgentController.RunResponse.class)
                .value(result -> {
                    assertThat(result.projectsProcessed()).isEqualTo(1);
                    assertThat(result.notesCreated()).isEqualTo(1);
                });

        client.post().uri("/api/v1/admin/agent/run")
                .exchange().expectStatus().isOk()
                .expectBody(AgentController.RunResponse.class)
                .value(result -> assertThat(result.notesCreated()).isZero());

        assertThat(noteRepository.findAll()).hasSize(1);
        Note note = noteRepository.findAll().get(0);
        assertThat(note.getProject().getId()).isEqualTo(projectId);
        assertThat(note.getAuthor()).isEqualTo(Note.Author.AGENT);
        assertThat(note.getTags()).containsExactly("agent-suggestion");
        assertThat(runLogRepository.findAll()).hasSize(2);
        assertThat(runLogRepository.findAll()).allSatisfy(log -> {
            assertThat(log.getFinishedAt()).isNotNull();
            assertThat(log.getError()).isNull();
        });
    }

    private Long createProject(WebTestClient client) {
        var area = new ru.wolf.api.lifearea.LifeAreaController.CreateLifeAreaRequest();
        area.setName("Работа");
        area.setColor("#3d5a4a");
        Long areaId = client.post().uri("/api/v1/life-areas").bodyValue(area).exchange()
                .expectStatus().isOk()
                .expectBody(ru.wolf.api.lifearea.LifeAreaController.LifeAreaResponse.class)
                .returnResult().getResponseBody().getId();

        var project = new ru.wolf.api.project.ProjectController.CreateProjectRequest();
        project.setLifeAreaId(areaId);
        project.setTitle("WOLF");
        project.setStartDate(java.time.LocalDate.now().minusDays(1));
        return client.post().uri("/api/v1/projects").bodyValue(project).exchange()
                .expectStatus().isOk()
                .expectBody(ru.wolf.api.project.ProjectController.ProjectResponse.class)
                .returnResult().getResponseBody().getId();
    }
}
