package ru.wolf.api.agentcontext;

import org.junit.jupiter.api.Test;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.gantt.WeekPlanRepository;
import ru.wolf.api.goal.GoalProjectRepository;
import ru.wolf.api.goal.GoalRepository;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.routine.RoutineRepository;
import ru.wolf.api.routine.RoutineScheduleRepository;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentContextServiceTest {
    @Test
    void builds_empty_user_scoped_context_without_raw_history() {
        UserRepository users = mock(UserRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        GoalRepository goals = mock(GoalRepository.class);
        GoalProjectRepository goalProjects = mock(GoalProjectRepository.class);
        RoutineRepository routines = mock(RoutineRepository.class);
        RoutineScheduleRepository schedules = mock(RoutineScheduleRepository.class);
        WeekPlanRepository plans = mock(WeekPlanRepository.class);
        TimeEntryRepository entries = mock(TimeEntryRepository.class);
        DeloProjectRepository links = mock(DeloProjectRepository.class);
        User user = User.builder().id(42L).username("alice").timezone("UTC").build();

        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        when(projects.findInProgressByUserOrderByTitleAsc(user)).thenReturn(List.of());
        when(goals.findByUserAndArchivedOrderByPriorityAsc(user, false)).thenReturn(List.of());
        when(routines.findByUserAndArchivedOrderByTitleAsc(user, false)).thenReturn(List.of());
        when(plans.findInWeekRange(any(), anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(List.of());
        when(entries.findOverlapping(anyLong(), any(), any())).thenReturn(List.of());

        AgentContext context = new AgentContextService(
                users, projects, goals, goalProjects, routines, schedules, plans, entries, links).build("alice");

        assertThat(context.projects()).isEmpty();
        assertThat(context.dynamics().historyAvailable()).isFalse();
        assertThat(context.prompt()).contains("нет данных расписания");
        assertThat(context.prompt()).doesNotContain("Запись времени #");
    }
}
