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
package ru.wolf.api.agent;

import ru.wolf.api.agent.dto.AgentRunResponse;
import ru.wolf.api.lifearea.dto.*;

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
import ru.wolf.api.note.assistant.FakeNotesAssistantAdapter;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.timeentry.TimeEntryRepository;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
class AgentApiIT extends ApiIntegrationTest {

    @Autowired
    private AgentJob agentJob;

    @Autowired
    private FakeNotesAssistantAdapter fakeNotesAssistant;

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
                .expectBody(AgentRunResponse.class)
                .value(result -> {
                    assertThat(result.projectsProcessed()).isEqualTo(1);
                    assertThat(result.notesCreated()).isEqualTo(1);
                });

        client.post().uri("/api/v1/admin/agent/run")
                .exchange().expectStatus().isOk()
                .expectBody(AgentRunResponse.class)
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
        var area = new CreateLifeAreaRequest("Работа", "#3d5a4a");
        Long areaId = client.post().uri("/api/v1/life-areas").bodyValue(area).exchange()
                .expectStatus().isOk()
                .expectBody(LifeAreaResponse.class)
                .returnResult().getResponseBody().id();

        var project = new ru.wolf.api.project.dto.CreateProjectRequest(
                areaId, null, "WOLF", null, null, java.time.LocalDate.now().minusDays(1), null, null, null);
        return client.post().uri("/api/v1/projects").bodyValue(project).exchange()
                .expectStatus().isOk()
                .expectBody(ru.wolf.api.project.dto.ProjectResponse.class)
                .returnResult().getResponseBody().id();
    }
}
