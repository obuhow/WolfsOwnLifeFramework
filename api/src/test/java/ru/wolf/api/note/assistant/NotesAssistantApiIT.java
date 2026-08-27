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
import ru.wolf.api.lifearea.dto.*;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.note.Note;
import ru.wolf.api.note.NoteRepository;
import ru.wolf.api.note.dto.NoteRequest;
import ru.wolf.api.note.dto.NoteResponse;
import ru.wolf.api.note.assistant.dto.ResumeResponse;
import ru.wolf.api.project.dto.*;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.support.ApiIntegrationTest;

@ActiveProfiles("test")
@TestPropertySource(properties = "wolf.llm.enabled=true")
class NotesAssistantApiIT extends ApiIntegrationTest {

    @Autowired
    private FakeNotesAssistantAdapter fakeNotesAssistant;

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
        Long projectId = createProject(client, "WOLF").id();

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new NamedByteArrayResource("voice memo".getBytes(StandardCharsets.UTF_8), "memo.webm"));
        parts.add("projectId", projectId.toString());
        parts.add("tags", "voice");

        client.post().uri("/api/v1/notes/audio")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(parts)
                .exchange()
                .expectStatus().isOk()
                .expectBody(NoteResponse.class)
                .value(note -> {
                    assertThat(note.body()).isEqualTo("Тестовая транскрипция");
                    assertThat(note.projectId()).isEqualTo(projectId);
                    assertThat(note.audioRef()).isNotBlank();
                    assertThat(note.tags()).containsExactly("voice");
                });
    }

    @Test
    void resume_summarizes_latest_project_notes_through_fake() {
        WebTestClient client = authedAdminClient();
        Long projectId = createProject(client, "WOLF").id();
        createNote(client, projectId, "Решили оставить JWT фильтр");
        createNote(client, projectId, "Остановился на проверке Spring Security");
        createNote(client, projectId, "Следом нужно добавить интеграционный тест");
        fakeNotesAssistant.setSummaryResponse("Остановился на Spring Security: JWT фильтр и интеграционный тест.");

        client.get().uri(uri -> uri.path("/api/v1/projects/{id}/resume").queryParam("limit", 10).build(projectId))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ResumeResponse.class)
                .value(resume -> {
                    assertThat(resume.projectId()).isEqualTo(projectId);
                    assertThat(resume.noteIds()).hasSize(3);
                    assertThat(resume.summary()).contains("Spring Security", "JWT", "интеграционный тест");
                });
    }


    private ProjectResponse createProject(WebTestClient client, String title) {
        var areaRequest = new CreateLifeAreaRequest("Работа", "#3d5a4a");
        Long areaId = client.post().uri("/api/v1/life-areas").bodyValue(areaRequest).exchange()
                .expectStatus().isOk().expectBody(LifeAreaResponse.class)
                .returnResult().getResponseBody().id();

        var projectRequest = new CreateProjectRequest(areaId, title);
        return client.post().uri("/api/v1/projects").bodyValue(projectRequest).exchange()
                .expectStatus().isOk().expectBody(ProjectResponse.class)
                .returnResult().getResponseBody();
    }

    private void createNote(WebTestClient client, Long projectId, String body) {
        var request = new NoteRequest(projectId, null, null, body, null);
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
