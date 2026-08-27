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

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.delo.DeloProject;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.backlog.BacklogItem;
import ru.wolf.api.backlog.BacklogItemRepository;
import ru.wolf.api.goal.Goal;
import ru.wolf.api.goal.GoalFactService;
import ru.wolf.api.goal.GoalWeekBudget;
import ru.wolf.api.goal.GoalWeekBudgetRepository;
import ru.wolf.api.goal.GoalRepository;
import ru.wolf.api.idea.Idea;
import ru.wolf.api.idea.IdeaRepository;
import ru.wolf.api.note.Note;
import ru.wolf.api.note.NoteRepository;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.morning.dto.*;
import ru.wolf.api.user.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read-only aggregation for the morning ritual. */
@Service
@RequiredArgsConstructor
public class MorningDigestService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final NoteRepository noteRepository;
    private final DeloProjectRepository deloProjectRepository;
    private final BacklogItemRepository backlogItemRepository;
    private final IdeaRepository ideaRepository;
    private final GoalRepository goalRepository;
    private final GoalWeekBudgetRepository goalWeekBudgetRepository;
    private final GoalFactService goalFactService;

    @Transactional(readOnly = true)
    public MorningDigestResponse build(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        String weekId = currentIsoWeek(user);
        GoalFactService.IsoWeek week = goalFactService.parseWeek(weekId);

        List<ProjectDigest> projects = projectRepository.findByUserOrderByTitleAsc(user).stream()
                .filter(project -> project.getStatus() == Project.Status.IN_PROGRESS)
                .map(project -> projectDigest(project, user, week))
                .toList();

        return new MorningDigestResponse(
                weekId, projects, selectIdeas(user), goalFactDigest(user, weekId, week));
    }

    private ProjectDigest projectDigest(
            Project project, User user, GoalFactService.IsoWeek week) {
        List<Note> notes = noteRepository.findByUserAndProjectIdOrderByCreatedAtDesc(
                project.getUser(), project.getId(), PageRequest.of(0, 5));
        List<NoteDigest> noteResponses = notes.stream()
                .map(note -> new NoteDigest(
                        note.getId(), note.getAuthor(), note.getBody(), note.getTags(),
                        note.getCreatedAt(), note.getUpdatedAt()))
                .toList();

        String period = "%d-W%02d".formatted(week.year(), week.week());
        Set<Long> queuedDeloIds = backlogItemRepository.findPeriod(user, BacklogItem.Scope.WEEK, period).stream()
                .map(item -> item.getDelo().getId()).collect(Collectors.toSet());

        List<DeloDigest> delos = deloProjectRepository.findByProjectId(project.getId()).stream()
                .map(DeloProject::getDelo)
                .filter(delo -> queuedDeloIds.contains(delo.getId()))
                .collect(Collectors.toMap(
                        delo -> delo.getId(), Function.identity(), (first, ignored) -> first))
                .values().stream()
                .sorted(Comparator.comparing(delo -> delo.getTitle().toLowerCase()))
                .limit(3)
                .map(delo -> new DeloDigest(delo.getId(), delo.getTitle()))
                .toList();
        return new ProjectDigest(project.getId(), project.getTitle(), noteResponses, delos);
    }

    private List<IdeaDigest> selectIdeas(User user) {
        List<Idea> bank = new ArrayList<>(ideaRepository.findForUser(user, null, Idea.Status.BANK));
        Collections.shuffle(bank);

        List<Idea> selected = new ArrayList<>();
        Set<Idea.Category> categories = new LinkedHashSet<>();
        for (Idea idea : bank) {
            if (selected.size() == 3) break;
            if (categories.add(idea.getCategory())) selected.add(idea);
        }
        for (Idea idea : bank) {
            if (selected.size() == 3) break;
            if (!selected.contains(idea)) selected.add(idea);
        }
        return selected.stream()
                .map(idea -> new IdeaDigest(
                        idea.getId(), idea.getTitle(), idea.getDescription(), idea.getCategory()))
                .toList();
    }

    private List<GoalFactDigest> goalFactDigest(
            User user, String weekId, GoalFactService.IsoWeek week) {
        List<Goal> goals = goalRepository.findByUserAndArchivedOrderByPriorityAsc(user, false);
        Map<Long, GoalWeekBudget> budgets = goals.isEmpty()
                ? Map.of()
                : goalWeekBudgetRepository.findByGoalIdInAndIsoYearAndIsoWeek(
                        goals.stream().map(Goal::getId).toList(), week.year(), week.week())
                .stream()
                .collect(Collectors.toMap(budget -> budget.getGoal().getId(), Function.identity()));

        return goals.stream()
                .map(goal -> new GoalFactDigest(
                        goal.getId(), goal.getTitle(),
                        budgetHours(budgets.get(goal.getId())),
                        goalFactService.calculate(user, goal, weekId), weekId))
                .toList();
    }

    private BigDecimal budgetHours(GoalWeekBudget budget) {
        return budget == null ? null : budget.getHours();
    }

    private String currentIsoWeek(User user) {
        LocalDate today = LocalDate.now(ZoneId.of(user.getTimezone()));
        WeekFields fields = WeekFields.ISO;
        return "%04d-W%02d".formatted(
                today.get(fields.weekBasedYear()), today.get(fields.weekOfWeekBasedYear()));
    }
}
