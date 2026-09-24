package ru.wolf.api.agentcontext;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.delo.DeloProject;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.gantt.WeekPlan;
import ru.wolf.api.gantt.WeekPlanRepository;
import ru.wolf.api.goal.Goal;
import ru.wolf.api.goal.GoalProjectRepository;
import ru.wolf.api.goal.GoalRepository;

import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.routine.Routine;
import ru.wolf.api.routine.RoutineRepository;
import ru.wolf.api.routine.RoutineSchedule;
import ru.wolf.api.routine.RoutineScheduleRepository;
import ru.wolf.api.timeentry.DayBounds;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Builds the bounded, user-scoped context consumed by the chat agent. */
@Service
@RequiredArgsConstructor
public class AgentContextService {
    public static final int DEFAULT_WEEKS = 26;
    public static final int MAX_WEEKS = 26;

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final GoalRepository goalRepository;
    private final GoalProjectRepository goalProjectRepository;
    private final RoutineRepository routineRepository;
    private final RoutineScheduleRepository routineScheduleRepository;
    private final WeekPlanRepository weekPlanRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final DeloProjectRepository deloProjectRepository;

    @Transactional(readOnly = true)
    public AgentContext build(String username) {
        return build(currentUser(username), DEFAULT_WEEKS);
    }

    @Transactional(readOnly = true)
    public AgentContext build(User user) {
        return build(user, DEFAULT_WEEKS);
    }

    @Transactional(readOnly = true)
    public AgentContext build(User user, int weeks) {
        if (weeks < 1 || weeks > MAX_WEEKS) {
            throw new IllegalArgumentException("Горизонт контекста должен быть от 1 до " + MAX_WEEKS + " недель");
        }
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDate currentMonday = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate from = currentMonday.minusWeeks(weeks - 1L);
        LocalDate toExclusive = currentMonday.plusWeeks(1);

        List<Project> projects = projectRepository.findInProgressByUserOrderByTitleAsc(user);
        List<Goal> goals = goalRepository.findByUserAndArchivedOrderByPriorityAsc(user, false);
        List<Routine> routines = routineRepository.findByUserAndArchivedOrderByTitleAsc(user, false);
        List<WeekPlan> plans = weekPlanRepository.findInWeekRange(
                user, isoYear(from), isoWeek(from), isoYear(currentMonday), isoWeek(currentMonday));
        LocalDateTimeRange range = range(user, from, toExclusive);
        List<TimeEntry> entries = timeEntryRepository.findOverlapping(user.getId(), range.from(), range.to());

        Map<Long, List<DeloProject>> linksByDelo = entries.stream()
                .filter(entry -> entry.getDelo() != null)
                .map(entry -> entry.getDelo().getId())
                .distinct()
                .collect(Collectors.toMap(id -> id, deloProjectRepository::findByDeloId));

        AgentContext.ScheduleDynamics dynamics = AgentScheduleAggregator.aggregate(
                user, from, toExclusive, projects, plans, entries, linksByDelo, routines);
        AgentContext contextWithoutPrompt = new AgentContext(
                java.time.Instant.now(),
                new AgentContext.Period(from, toExclusive, weeks),
                projects.stream().map(this::projectSummary).toList(),
                goals.stream().map(this::goalSummary).toList(),
                routines.stream().map(this::routineSummary).toList(),
                dynamics,
                "pending");
        return new AgentContext(
                contextWithoutPrompt.generatedAt(), contextWithoutPrompt.period(), contextWithoutPrompt.projects(),
                contextWithoutPrompt.goals(), contextWithoutPrompt.routines(), dynamics,
                AgentContextFormatter.format(contextWithoutPrompt));
    }

    private AgentContext.ProjectSummary projectSummary(Project project) {
        return new AgentContext.ProjectSummary(project.getId(), project.getTitle(), project.getLifeArea().getName(),
                project.getDescription(), project.getStartDate(), project.getEndDate(), project.getTotalPlanHours());
    }

    private AgentContext.GoalSummary goalSummary(Goal goal) {
        List<String> projectTitles = goalProjectRepository.findByGoalId(goal.getId()).stream()
                .map(link -> link.getProject().getTitle()).toList();
        return new AgentContext.GoalSummary(goal.getId(), goal.getTitle(), goal.getDescription(),
                goal.getPriority(), projectTitles);
    }

    private AgentContext.RoutineSummary routineSummary(Routine routine) {
        List<String> schedule = routineScheduleRepository.findByRoutineIdOrderByDayOfWeekAscStartTimeAsc(routine.getId()).stream()
                .map(this::scheduleLabel).toList();
        return new AgentContext.RoutineSummary(routine.getId(), routine.getTitle(), routine.getDescription(),
                routine.getWeeklyHours(), schedule);
    }

    private String scheduleLabel(RoutineSchedule schedule) {
        return schedule.getDayOfWeek().name() + " " + schedule.getStartTime() + "-" + schedule.getEndTime();
    }

    private LocalDateTimeRange range(User user, LocalDate from, LocalDate toExclusive) {
        java.time.LocalTime dayEnd = user.getDayEnd() == null ? java.time.LocalTime.of(2, 0) : user.getDayEnd();
        return new LocalDateTimeRange(DayBounds.forDay(from, dayEnd).start(), DayBounds.forDay(toExclusive, dayEnd).start());
    }

    private int isoYear(LocalDate date) { return date.get(WeekFields.ISO.weekBasedYear()); }
    private int isoWeek(LocalDate date) { return date.get(WeekFields.ISO.weekOfWeekBasedYear()); }

    private User currentUser(String username) {
        return userRepository.findByUsername(username).orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private record LocalDateTimeRange(java.time.LocalDateTime from, java.time.LocalDateTime to) {
    }
}
