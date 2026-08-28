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
 * along with this program. if not, see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.delo.DeloService;
import ru.wolf.api.delo.dto.CreateDeloRequest;
import ru.wolf.api.delo.dto.DeloResponse;
import ru.wolf.api.importer.dto.ConfirmCandidate;
import ru.wolf.api.importer.dto.ConfirmImportRequest;
import ru.wolf.api.importer.dto.ConfirmImportResponse;
import ru.wolf.api.importer.dto.CreatedEntity;
import ru.wolf.api.importer.dto.EntityKind;
import ru.wolf.api.importer.dto.ParsedField;
import ru.wolf.api.lifearea.LifeArea;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectService;
import ru.wolf.api.project.dto.CreateProjectRequest;
import ru.wolf.api.project.dto.ProjectResponse;
import ru.wolf.api.recurrence.RecurrenceService;
import ru.wolf.api.routine.RoutineService;
import ru.wolf.api.routine.dto.RoutineRequest;
import ru.wolf.api.routine.dto.RoutineResponse;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.timeentry.TimeEntry.Status;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

/**
 * DB-free unit tests for the chat-panel confirm path (release 0.7, ticket 02).
 * Exercises the kind dispatch and channel-specific glue (projectRef link,
 * parallel time-entry slot) without a Spring context or a real LLM.
 */
@ExtendWith(MockitoExtension.class)
class ImportConfirmServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private DeloRepository deloRepository;
    @Mock private DeloService deloService;
    @Mock private ProjectService projectService;
    @Mock private RoutineService routineService;
    @Mock private RecurrenceService recurrenceService;
    @Mock private LifeAreaRepository lifeAreaRepository;
    @Mock private TimeEntryRepository timeEntryRepository;

    private ImportConfirmService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new ImportConfirmService(userRepository, deloRepository, deloService,
                projectService, routineService, recurrenceService, lifeAreaRepository, timeEntryRepository);
        user = User.builder().id(1L).username("alice").timezone("Europe/Moscow").build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
    }

    private static ParsedField f(String name, String value, boolean confident) {
        return confident ? ParsedField.confident(name, value) : ParsedField.needsConfirmation(name, value);
    }

    private ConfirmCandidate delo(String title, String startAt, String duration, String projectRef) {
        List<ParsedField> fields = new java.util.ArrayList<>();
        fields.add(f("title", title, true));
        if (startAt != null) fields.add(f("startAt", startAt, true));
        if (duration != null) fields.add(f("durationMinutes", duration, false));
        if (projectRef != null) fields.add(f("projectRef", projectRef, true));
        return new ConfirmCandidate(EntityKind.DELO, fields);
    }

    @Test
    void delo_withoutStart_doesNotCreateTimeEntry() {
        when(deloService.createDelo(eq("alice"), any(CreateDeloRequest.class)))
                .thenReturn(new DeloResponse(1L, "тренировка", null, Delo.ExecutionMode.SELF, List.of(), null));

        ConfirmImportResponse res = service.confirm("alice",
                new ConfirmImportRequest(List.of(delo("тренировка", null, "60", null))));

        assertThat(res.created()).hasSize(1);
        assertThat(res.created().get(0).kind()).isEqualTo(EntityKind.DELO);
        assertThat(res.created().get(0).link()).isEqualTo("/delos/1");
        verify(deloService, times(1)).createDelo(any(), any());
        verify(timeEntryRepository, never()).save(any());
    }

    @Test
    void delo_withStart_placesParallelTimeEntry() {
        when(deloService.createDelo(eq("alice"), any(CreateDeloRequest.class)))
                .thenReturn(new DeloResponse(1L, "тренировка", null, Delo.ExecutionMode.SELF, List.of(), null));
        when(deloRepository.findById(1L))
                .thenReturn(Optional.of(Delo.builder().id(1L).user(user).title("тренировка").build()));

        ConfirmImportResponse res = service.confirm("alice",
                new ConfirmImportRequest(List.of(delo("тренировка", "2020-01-01T10:00", "90", null))));

        assertThat(res.created()).hasSize(1);
        ArgumentCaptor<TimeEntry> captor = ArgumentCaptor.forClass(TimeEntry.class);
        verify(timeEntryRepository, times(1)).save(captor.capture());
        TimeEntry entry = captor.getValue();
        assertThat(entry.getStartAt()).isEqualTo(LocalDateTime.parse("2020-01-01T10:00"));
        assertThat(entry.getEndAt()).isEqualTo(LocalDateTime.parse("2020-01-01T11:30"));
        // 2020 is in the past → DONE (occupancy is always parallel / CREATE_OVER)
        assertThat(entry.getStatus()).isEqualTo(Status.DONE);
    }

    @Test
    void delo_withProjectRef_linksProject() {
        when(deloService.createDelo(eq("alice"), any(CreateDeloRequest.class)))
                .thenReturn(new DeloResponse(1L, "звонок", null, Delo.ExecutionMode.SELF, List.of(), null));
        when(projectService.listProjects(eq("alice"), any()))
                .thenReturn(List.of(new ProjectResponse(10L, null, null, "Ремонт", null, null, null, null, null, null)));

        service.confirm("alice",
                new ConfirmImportRequest(List.of(delo("звонок", null, "30", "Ремонт"))));

        verify(deloService, times(1)).linkProject("alice", 1L, 10L);
    }

    @Test
    void project_createdInBotLifeArea() {
        LifeArea area = LifeArea.builder().id(5L).user(user).name("Импорт").build();
        when(lifeAreaRepository.findByUserAndNameIgnoreCase(user, "Импорт")).thenReturn(Optional.of(area));
        when(projectService.createProject(eq("alice"), any(CreateProjectRequest.class)))
                .thenReturn(new ProjectResponse(10L, 5L, null, "Ремонт", null, null, null, null, null, null));

        ConfirmImportResponse res = service.confirm("alice",
                new ConfirmImportRequest(List.of(new ConfirmCandidate(EntityKind.PROJECT,
                        List.of(f("title", "Ремонт", true))))));

        assertThat(res.created()).hasSize(1);
        assertThat(res.created().get(0).kind()).isEqualTo(EntityKind.PROJECT);
        assertThat(res.created().get(0).link()).isEqualTo("/projects/10");

        ArgumentCaptor<CreateProjectRequest> captor = ArgumentCaptor.forClass(CreateProjectRequest.class);
        verify(projectService, times(1)).createProject(eq("alice"), captor.capture());
        assertThat(captor.getValue().lifeAreaId()).isEqualTo(5L);
    }

    @Test
    void recurrence_appliesRuleViaRecurrenceService() {
        when(deloService.createDelo(eq("alice"), any(CreateDeloRequest.class)))
                .thenReturn(new DeloResponse(2L, "английский", null, Delo.ExecutionMode.SELF, List.of(), null));

        ConfirmCandidate cand = new ConfirmCandidate(EntityKind.RECURRENCE, List.of(
                f("title", "английский", true),
                f("recurrenceWeekday", "FRIDAY", true),
                f("recurrenceTime", "19:00", true),
                f("horizonWeeks", "12", true)));
        service.confirm("alice", new ConfirmImportRequest(List.of(cand)));

        verify(recurrenceService, times(1)).apply(eq(user), eq(2L), any(RecurrenceService.ApplyCommand.class));
    }

    @Test
    void routine_createdViaRoutineService() {
        when(routineService.create(eq("alice"), any(RoutineRequest.class)))
                .thenReturn(ResponseEntity.ok(new RoutineResponse(20L, "утро", null, BigDecimal.ZERO,
                        null, null, false, List.of(), List.of(), List.of())));

        ConfirmImportResponse res = service.confirm("alice",
                new ConfirmImportRequest(List.of(new ConfirmCandidate(EntityKind.ROUTINE,
                        List.of(f("title", "утро", true))))));

        assertThat(res.created()).hasSize(1);
        assertThat(res.created().get(0).kind()).isEqualTo(EntityKind.ROUTINE);
        assertThat(res.created().get(0).link()).isEqualTo("/routines");
    }

    @Test
    void emptyTitle_skipped() {
        ConfirmCandidate cand = new ConfirmCandidate(EntityKind.DELO, List.of(f("title", "  ", true)));
        ConfirmImportResponse res = service.confirm("alice", new ConfirmImportRequest(List.of(cand)));
        assertThat(res.created()).isEmpty();
        verify(deloService, never()).createDelo(any(), any());
    }
}
