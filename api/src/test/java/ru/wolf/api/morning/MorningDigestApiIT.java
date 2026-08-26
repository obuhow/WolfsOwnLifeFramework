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
package ru.wolf.api.morning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.delo.DeloController;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.backlog.BacklogItemRepository;
import ru.wolf.api.goal.GoalController;
import ru.wolf.api.goal.GoalProjectRepository;
import ru.wolf.api.goal.GoalRepository;
import ru.wolf.api.goal.GoalWeekBudgetRepository;
import ru.wolf.api.idea.Idea;
import ru.wolf.api.idea.IdeaController;
import ru.wolf.api.idea.IdeaRepository;
import ru.wolf.api.lifearea.LifeAreaController;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.note.Note;
import ru.wolf.api.note.NoteController;
import ru.wolf.api.note.NoteRepository;
import ru.wolf.api.project.ProjectController;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.timeentry.TimeEntryRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MorningDigestApiIT extends ApiIntegrationTest {

    @Autowired NoteRepository noteRepository;
    @Autowired DeloProjectRepository deloProjectRepository;
    @Autowired DeloRepository deloRepository;
    @Autowired BacklogItemRepository backlogItemRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired LifeAreaRepository lifeAreaRepository;
    @Autowired IdeaRepository ideaRepository;
    @Autowired GoalWeekBudgetRepository goalWeekBudgetRepository;
    @Autowired GoalProjectRepository goalProjectRepository;
    @Autowired GoalRepository goalRepository;
    @Autowired TimeEntryRepository timeEntryRepository;

    @BeforeEach
    void cleanup() {
        noteRepository.deleteAll();
        goalWeekBudgetRepository.deleteAll();
        goalProjectRepository.deleteAll();
        goalRepository.deleteAll();
        backlogItemRepository.deleteAll();
        deloProjectRepository.deleteAll();
        timeEntryRepository.deleteAll();
        deloRepository.deleteAll();
        ideaRepository.deleteAll();
        projectRepository.deleteAll();
        lifeAreaRepository.deleteAll();
    }

    @Test
    void digest_contains_project_notes_delos_ideas_and_goal_fact() {
        WebTestClient client = authedAdminClient();
        String week = currentIsoWeek();
        Long areaId = createLifeArea(client, "Работа");
        ProjectController.ProjectResponse project = createProject(client, areaId, "Утренний проект");

        List<Long> deloIds = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            deloIds.add(createDelo(client, "Дело " + i, List.of(project.getId())));
        }
        for (Long deloId : deloIds) {
            client.post().uri(uri -> uri.path("/api/v1/backlog/week/{year}/{week}/delos/{deloId}")
                            .build(Integer.parseInt(week.substring(0, 4)), Integer.parseInt(week.substring(6)), deloId))
                    .exchange().expectStatus().isOk();
        }
        for (int i = 1; i <= 5; i++) {
            NoteController.NoteRequest request = new NoteController.NoteRequest();
            request.setProjectId(project.getId());
            request.setAuthor(i == 5 ? Note.Author.AGENT : Note.Author.USER);
            request.setBody("Заметка " + i);
            client.post().uri("/api/v1/notes").bodyValue(request)
                    .exchange().expectStatus().isOk();
        }

        createIdea(client, "Бизнес", Idea.Category.BUSINESS);
        createIdea(client, "Музыка", Idea.Category.MUSIC);
        createIdea(client, "Личное", Idea.Category.PERSONAL);
        createIdea(client, "Криповое", Idea.Category.CREEPY);

        GoalController.GoalResponse goal = createGoal(client, "Главная цель");
        client.post().uri("/api/v1/goals/{id}/budget", goal.getId())
                .bodyValue(new GoalController.BudgetRequest(week, new BigDecimal("8")))
                .exchange().expectStatus().isOk();
        client.post().uri("/api/v1/goals/{id}/projects/{projectId}", goal.getId(), project.getId())
                .exchange().expectStatus().isNoContent();
        client.put().uri("/api/v1/time-entries")
                .bodyValue(Map.of(
                        "startAt", LocalDate.now() + "T10:00:00",
                        "endAt", LocalDate.now() + "T12:00:00",
                        "deloId", deloIds.get(0),
                        "status", "DONE"))
                .exchange().expectStatus().isOk();

        MorningDigestController.MorningDigestResponse response = client.get()
                .uri("/api/v1/morning-digest")
                .exchange().expectStatus().isOk()
                .expectBody(MorningDigestController.MorningDigestResponse.class)
                .returnResult().getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.getWeekId()).isEqualTo(week);
        assertThat(response.getProjects()).hasSize(1);
        assertThat(response.getProjects().get(0).getLastNotes()).hasSize(5);
        assertThat(response.getProjects().get(0).getTopDelos()).hasSize(3);
        assertThat(response.getProjects().get(0).getLastNotes())
                .extracting(MorningDigestController.NoteDigest::getBody)
                .containsExactly("Заметка 5", "Заметка 4", "Заметка 3", "Заметка 2", "Заметка 1");
        assertThat(response.getIdeas()).hasSize(3);
        assertThat(response.getIdeas()).extracting(MorningDigestController.IdeaDigest::getCategory)
                .doesNotHaveDuplicates();
        assertThat(response.getGoalsFact()).singleElement()
                .extracting(MorningDigestController.GoalFactDigest::getBudgetHours,
                        MorningDigestController.GoalFactDigest::getFactHours)
                .containsExactly(new BigDecimal("8.00"), new BigDecimal("2.00"));
    }

    @Test
    void digest_returns_only_active_projects_and_bank_ideas() {
        WebTestClient client = authedAdminClient();
        Long areaId = createLifeArea(client, "Работа");
        ProjectController.ProjectResponse archived = createProject(client, areaId, "Архивный");
        client.put().uri("/api/v1/projects/{id}", archived.getId())
                .bodyValue(new ProjectController.UpdateProjectRequest(
                        areaId, null, "Архивный", ru.wolf.api.project.Project.Status.ARCHIVED,
                        "", null, null, null))
                .exchange().expectStatus().isOk();
        createIdea(client, "В банке", Idea.Category.PERSONAL);
        IdeaController.CreateIdeaRequest inWork = new IdeaController.CreateIdeaRequest(
                "В работе", null, Idea.Category.BUSINESS, Idea.Status.IN_WORK);
        client.post().uri("/api/v1/ideas").bodyValue(inWork).exchange().expectStatus().isOk();

        MorningDigestController.MorningDigestResponse response = client.get()
                .uri("/api/v1/morning-digest")
                .exchange().expectStatus().isOk()
                .expectBody(MorningDigestController.MorningDigestResponse.class)
                .returnResult().getResponseBody();

        assertThat(response.getProjects()).isEmpty();
        assertThat(response.getIdeas()).extracting(MorningDigestController.IdeaDigest::getTitle)
                .containsExactly("В банке");
    }

    private Long createLifeArea(WebTestClient client, String name) {
        return client.post().uri("/api/v1/life-areas")
                .bodyValue(new LifeAreaController.CreateLifeAreaRequest(name, "#123456"))
                .exchange().expectStatus().isOk()
                .expectBody(LifeAreaController.LifeAreaResponse.class)
                .returnResult().getResponseBody().getId();
    }

    private ProjectController.ProjectResponse createProject(WebTestClient client, Long areaId, String title) {
        ProjectController.CreateProjectRequest request = new ProjectController.CreateProjectRequest();
        request.setLifeAreaId(areaId);
        request.setTitle(title);
        return client.post().uri("/api/v1/projects").bodyValue(request)
                .exchange().expectStatus().isOk()
                .expectBody(ProjectController.ProjectResponse.class)
                .returnResult().getResponseBody();
    }

    private Long createDelo(WebTestClient client, String title, List<Long> projectIds) {
        DeloController.CreateDeloRequest request = new DeloController.CreateDeloRequest();
        request.setTitle(title);
        request.setProjectIds(projectIds);
        request.setPrimaryProjectId(projectIds.get(0));
        return client.post().uri("/api/v1/delos").bodyValue(request)
                .exchange().expectStatus().isOk()
                .expectBody(DeloController.DeloResponse.class)
                .returnResult().getResponseBody().getId();
    }

    private void createIdea(WebTestClient client, String title, Idea.Category category) {
        client.post().uri("/api/v1/ideas")
                .bodyValue(new IdeaController.CreateIdeaRequest(title, null, category, Idea.Status.BANK))
                .exchange().expectStatus().isOk();
    }

    private GoalController.GoalResponse createGoal(WebTestClient client, String title) {
        return client.post().uri("/api/v1/goals")
                .bodyValue(new GoalController.CreateGoalRequest(title, null, 1))
                .exchange().expectStatus().isOk()
                .expectBody(GoalController.GoalResponse.class)
                .returnResult().getResponseBody();
    }

    private String currentIsoWeek() {
        LocalDate today = LocalDate.now();
        WeekFields fields = WeekFields.ISO;
        return "%04d-W%02d".formatted(
                today.get(fields.weekBasedYear()), today.get(fields.weekOfWeekBasedYear()));
    }
    }
