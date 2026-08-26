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

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.note.Note;
import ru.wolf.api.note.NoteRepository;
import ru.wolf.api.note.assistant.NotesAssistant;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class AgentJob {

    private static final int ACTIVE_DAYS = 14;

    private final AgentRunLogService runLogService;
    private final ProjectRepository projectRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final DeloProjectRepository deloProjectRepository;
    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final NotesAssistant notesAssistant;

    public AgentJob(
            AgentRunLogService runLogService,
            ProjectRepository projectRepository,
            TimeEntryRepository timeEntryRepository,
            DeloProjectRepository deloProjectRepository,
            NoteRepository noteRepository,
            UserRepository userRepository,
            NotesAssistant notesAssistant
    ) {
        this.runLogService = runLogService;
        this.projectRepository = projectRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.deloProjectRepository = deloProjectRepository;
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
        this.notesAssistant = notesAssistant;
    }

    @Scheduled(cron = "${wolf.agent.cron:0 0 4 * * *}")
    public void scheduledRun() {
        runForAllUsers();
    }

    public AgentRunResult runForAllUsers() {
        int projects = 0;
        int notes = 0;
        int logs = 0;
        for (User user : userRepository.findByAccountTypeAndStatus("REGULAR", "ACTIVE")) {
            AgentRunResult result = runForUserInTransaction(user);
            projects += result.projectsProcessed();
            notes += result.notesCreated();
            logs++;
        }
        return new AgentRunResult(projects, notes, logs);
    }

    @Transactional
    public AgentRunResult runForUser(User user) {
        return runForUserInTransaction(user);
    }

    private AgentRunResult runForUserInTransaction(User user) {
        Instant startedAt = Instant.now();
        AgentRunLog log = AgentRunLog.builder().user(user).startedAt(startedAt).build();
        runLogService.save(log);
        try {
            ZoneId zone = ZoneId.of(user.getTimezone());
            LocalDate today = LocalDate.now(zone);
            LocalDateTime from = today.minusDays(ACTIVE_DAYS).atStartOfDay();
            LocalDateTime to = today.plusDays(1).atStartOfDay();
            List<Project> activeProjects = projectRepository.findByUserOrderByTitleAsc(user).stream()
                    .filter(project -> isActive(project, user.getId(), from, to, today))
                    .toList();

            int created = 0;
            for (Project project : activeProjects) {
                Instant fromInstant = today.atStartOfDay(zone).toInstant();
                Instant toInstant = today.plusDays(1).atStartOfDay(zone).toInstant();
                if (noteRepository.existsByUserAndProjectIdAndAuthorAndTagAndCreatedAtBetween(
                        user, project.getId(), Note.Author.AGENT.name(), "agent-suggestion", fromInstant, toInstant)) {
                    continue;
                }
                List<String> topics = topicsFor(project);
                String suggestion = notesAssistant.suggest(project.getId(), topics);
                if (suggestion == null || suggestion.isBlank()) {
                    continue;
                }
                noteRepository.save(Note.builder()
                        .user(user)
                        .project(project)
                        .author(Note.Author.AGENT)
                        .body(suggestion.trim())
                        .tags(new String[]{"agent-suggestion"})
                        .build());
                created++;
            }
            log.setFinishedAt(Instant.now());
            log.setProjectsProcessed(activeProjects.size());
            log.setNotesCreated(created);
            runLogService.save(log);
            return new AgentRunResult(activeProjects.size(), created, 1);
        } catch (RuntimeException ex) {
            log.setFinishedAt(Instant.now());
            log.setError(errorMessage(ex));
            runLogService.save(log);
            throw ex;
        }
    }

    private boolean isActive(Project project, Long userId, LocalDateTime from, LocalDateTime to, LocalDate today) {
        if (project.getStatus() == Project.Status.ARCHIVED) {
            return false;
        }
        boolean hasRecentTime = !timeEntryRepository.findOverlapping(userId, from, to).stream()
                .filter(entry -> entry.getDelo() != null)
                .noneMatch(entry -> deloProjectRepository.findByDeloId(entry.getDelo().getId()).stream()
                        .anyMatch(link -> link.getProject().getId().equals(project.getId())));
        boolean inProgress = project.getStartDate() != null
                && !project.getStartDate().isAfter(today)
                && (project.getEndDate() == null || !project.getEndDate().isBefore(today));
        return hasRecentTime || inProgress;
    }

    private List<String> topicsFor(Project project) {
        List<String> topics = new ArrayList<>();
        topics.add(project.getTitle());
        if (project.getDescription() != null && !project.getDescription().isBlank()) {
            topics.addAll(Arrays.stream(project.getDescription().split("\\s+"))
                    .filter(word -> word.length() >= 4)
                    .limit(8)
                    .toList());
        }
        return topics;
    }

    private String errorMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return ex.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    public record AgentRunResult(int projectsProcessed, int notesCreated, int runsLogged) {
    }
}
