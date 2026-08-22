package ru.wolf.api.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.goal.Goal;
import ru.wolf.api.goal.GoalMetric;
import ru.wolf.api.goal.GoalMetricRepository;
import ru.wolf.api.goal.GoalProject;
import ru.wolf.api.goal.GoalProjectId;
import ru.wolf.api.goal.GoalProjectRepository;
import ru.wolf.api.goal.GoalRepository;
import ru.wolf.api.goal.GoalWeekBudget;
import ru.wolf.api.goal.GoalWeekBudgetRepository;
import ru.wolf.api.idea.Idea;
import ru.wolf.api.idea.IdeaRepository;
import ru.wolf.api.lifearea.LifeArea;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.lifesphere.LifeSphere;
import ru.wolf.api.lifesphere.LifeSphereRepository;
import ru.wolf.api.lifesphere.LifeSphereSeeder;
import ru.wolf.api.lifesphere.Synergy;
import ru.wolf.api.lifesphere.SynergyRepository;
import ru.wolf.api.note.Note;
import ru.wolf.api.note.NoteRepository;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectDependency;
import ru.wolf.api.project.ProjectDependencyRepository;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.routine.Routine;
import ru.wolf.api.routine.RoutineRepository;
import ru.wolf.api.routine.RoutineSchedule;
import ru.wolf.api.routine.RoutineScheduleRepository;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;
import ru.wolf.api.backlog.BacklogItem;
import ru.wolf.api.backlog.BacklogItemRepository;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Generates deterministic, realistic demo fixtures for any user.
 * All dates are relative to "today" in the user's timezone.
 * Deterministic: uses a fixed seed based on user ID for reproducible results.
 */
@Component
@RequiredArgsConstructor
public class DemoFixtureGenerator {

    private final LifeSphereSeeder lifeSphereSeeder;
    private final LifeAreaRepository lifeAreaRepository;
    private final LifeSphereRepository lifeSphereRepository;
    private final ProjectRepository projectRepository;
    private final DeloRepository deloRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final GoalRepository goalRepository;
    private final GoalMetricRepository goalMetricRepository;
    private final GoalWeekBudgetRepository goalWeekBudgetRepository;
    private final GoalProjectRepository goalProjectRepository;
    private final IdeaRepository ideaRepository;
    private final NoteRepository noteRepository;
    private final SynergyRepository synergyRepository;
    private final ProjectDependencyRepository projectDependencyRepository;
    private final RoutineRepository routineRepository;
    private final RoutineScheduleRepository routineScheduleRepository;
    private final BacklogItemRepository backlogItemRepository;

    /**
     * Populates the given user with a complete demo dataset.
     * Idempotent on empty user (fails if user already has data).
     */
    @Transactional
    public void populate(User user) {
        // Check if user already has data
        if (!projectRepository.findByUserOrderByTitleAsc(user).isEmpty()) {
            throw new IllegalStateException("User already has data, refusing to populate");
        }
        if (!deloRepository.findByUserOrderByTitleAsc(user).isEmpty()) {
            throw new IllegalStateException("User already has delos, refusing to populate");
        }
        if (!timeEntryRepository.findOverlapping(user.getId(), 
                LocalDateTime.now().minusDays(14), LocalDateTime.now().plusDays(1)).isEmpty()) {
            throw new IllegalStateException("User already has time entries, refusing to populate");
        }

        // Use deterministic random based on user ID
        Random random = new Random(user.getId() ^ 0x5A5A5A5AL);
        
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDate today = LocalDate.now(zone);
        
        // 1. Seed 9 Life Spheres
        lifeSphereSeeder.seed(user);
        List<LifeSphere> spheres = lifeSphereRepository.findByUserOrderBySortOrderAscNameAsc(user);
        
        // 2. Create 3 Life Areas
        LifeArea workArea = createLifeArea(user, "Работа", 0, "#3B82F6");
        LifeArea healthArea = createLifeArea(user, "Здоровье", 1, "#10B981");
        LifeArea growthArea = createLifeArea(user, "Развитие", 2, "#8B5CF6");
        List<LifeArea> areas = List.of(workArea, healthArea, growthArea);
        
        // 3. Create 4 Projects
        Project redesign = createProject(user, workArea, null, "Редизайн сайта", 
                Project.Status.IN_PROGRESS, today.minusDays(30), today.plusDays(60), 
                BigDecimal.valueOf(40), Project.PlanDistribution.EVEN_WEEKDAYS);
        Project apiDev = createProject(user, workArea, null, "Разработка API", 
                Project.Status.IN_PROGRESS, today.minusDays(15), today.plusDays(90), 
                BigDecimal.valueOf(60), Project.PlanDistribution.EVEN_WEEKDAYS);
        Project javaCourse = createProject(user, growthArea, null, "Курс по Java", 
                Project.Status.IN_PROGRESS, today.minusDays(10), today.plusDays(120), 
                BigDecimal.valueOf(30), Project.PlanDistribution.EVEN_WEEKDAYS);
        Project personalGoals = createProject(user, healthArea, null, "Личные цели", 
                Project.Status.IN_PROGRESS, today, today.plusDays(180), 
                BigDecimal.valueOf(20), Project.PlanDistribution.NONE);
        List<Project> projects = List.of(redesign, apiDev, javaCourse, personalGoals);
        
        // 4. Create 12-15 Delos (some without project)
        List<Delo> delos = new ArrayList<>();
        // Project-bound delos
        delos.add(createDelo(user, "Дизайн макета главной", redesign, "Согласовать с заказчиком", LocalTime.of(10, 0), LocalTime.of(12, 0)));
        delos.add(createDelo(user, "Верстка компонентов", redesign, "UI-кит готов", LocalTime.of(13, 0), LocalTime.of(17, 0)));
        delos.add(createDelo(user, "Код-ревью PR #42", apiDev, "Проверить тесты", LocalTime.of(11, 0), LocalTime.of(12, 0)));
        delos.add(createDelo(user, "Написание OpenAPI спеки", apiDev, "Swagger документация", LocalTime.of(14, 0), LocalTime.of(16, 0)));
        delos.add(createDelo(user, "Модуль 1: Основы Java", javaCourse, "Уроки 1-5", LocalTime.of(19, 0), LocalTime.of(21, 0)));
        delos.add(createDelo(user, "Модуль 2: Коллекции", javaCourse, "Практикум", LocalTime.of(19, 0), LocalTime.of(21, 0)));
        delos.add(createDelo(user, "Утренняя пробежка", personalGoals, "5 км в парке", LocalTime.of(7, 0), LocalTime.of(8, 0)));
        delos.add(createDelo(user, "Медитация", personalGoals, "Headspace 10 мин", LocalTime.of(8, 0), LocalTime.of(8, 15)));
        // No-project delos (Без проекта)
        delos.add(createDelo(user, "Покупки на неделю", null, "Продукты + бытовая химия", LocalTime.of(18, 0), LocalTime.of(19, 0)));
        delos.add(createDelo(user, "Звонок маме", null, "Рассказать про проект", LocalTime.of(20, 0), LocalTime.of(20, 30)));
        delos.add(createDelo(user, "Чтение книги", null, "Глава 3-4", LocalTime.of(22, 0), LocalTime.of(22, 45)));
        delos.add(createDelo(user, "Планирование недели", null, "Обзор календаря + бэклог", LocalTime.of(9, 0), LocalTime.of(9, 30)));
        
        // 5. Generate Time Entries for last 14 days including today
        generateTimeEntries(user, delos, today, zone, random);
        
        // 6. Weekly Plans (current and next ISO week)
        generateWeekPlans(user, projects, today, random);
        
        // 7. Goals (3 with weekly budgets and metrics)
        generateGoals(user, projects, today, zone, random);
        
        // 8. Synergies (5-6 with different impacts)
        generateSynergies(user, projects, spheres, random);
        
        // 9. Ideas (4 in different states)
        generateIdeas(user, today, random);
        
        // 10. Notes (3, one from Agent)
        generateNotes(user, projects, delos, today, random);
        
        // 11. Project Dependencies (at least 1)
        generateProjectDependencies(user, projects);
        
        // 12. Backlog items (current week)
        generateBacklogItems(user, delos, today, random);
        
        // 13. Routines (2-3 with schedules)
        generateRoutines(user, areas, today, zone, random);
    }

    private LifeArea createLifeArea(User user, String name, int sortOrder, String color) {
        LifeArea area = LifeArea.builder()
                .user(user)
                .name(name)
                .sortOrder(sortOrder)
                .color(color)
                .build();
        return lifeAreaRepository.save(area);
    }

    private Project createProject(User user, LifeArea lifeArea, Project parent, 
                                  String title, Project.Status status, 
                                  LocalDate startDate, LocalDate endDate, 
                                  BigDecimal totalPlanHours, Project.PlanDistribution planDistribution) {
        Project project = Project.builder()
                .user(user)
                .lifeArea(lifeArea)
                .parent(parent)
                .title(title)
                .status(status)
                .description("Демо-проект: " + title)
                .startDate(startDate)
                .endDate(endDate)
                .totalPlanHours(totalPlanHours)
                .planDistribution(planDistribution)
                .planFrozenAt(LocalDate.now().with(java.time.temporal.TemporalAdjusters.firstDayOfMonth()))
                .build();
        return projectRepository.save(project);
    }

    private Delo createDelo(User user, String title, Project project, String description,
                            LocalTime startTime, LocalTime endTime) {
        Delo delo = Delo.builder()
                .user(user)
                .title(title)
                .description(description)
                .executionMode(Delo.ExecutionMode.SELF)
                .build();
        return deloRepository.save(delo);
    }

    private void generateTimeEntries(User user, List<Delo> delos, LocalDate today, ZoneId zone, Random random) {
        LocalTime nightStart = user.getNightStart();
        LocalTime dayEnd = user.getDayEnd();
        LocalTime defaultSleepEnd = user.getDefaultSleepEnd();
        
        // Generate for last 14 days
        for (int dayOffset = -13; dayOffset <= 0; dayOffset++) {
            LocalDate date = today.plusDays(dayOffset);
            boolean isToday = (dayOffset == 0);
            boolean isWeekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
            
            // Target hours for the day (workday vs weekend)
            double targetHours = isWeekend ? 2.0 : (4.0 + random.nextDouble() * 3.0); // 4-7h workday, 2h weekend
            
            // Add sleep entry
            LocalDateTime sleepStart = date.atTime(nightStart).atZone(zone).toLocalDateTime();
            LocalDateTime sleepEnd = date.atTime(defaultSleepEnd).atZone(zone).toLocalDateTime();
            if (sleepEnd.isBefore(sleepStart)) {
                sleepEnd = sleepEnd.plusDays(1);
            }
            timeEntryRepository.save(TimeEntry.builder()
                    .user(user)
                    .startAt(sleepStart)
                    .endAt(sleepEnd)
                    .status(TimeEntry.Status.DONE)
                    .build());
            
            // Generate work/personal entries
            List<Delo> availableDelos = new ArrayList<>(delos);
            // Shuffle deterministically
            for (int i = availableDelos.size() - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                var temp = availableDelos.get(i);
                availableDelos.set(i, availableDelos.get(j));
                availableDelos.set(j, temp);
            }
            
            double accumulatedHours = 0;
            LocalTime currentTime = defaultSleepEnd;
            
            for (Delo delo : availableDelos) {
                if (accumulatedHours >= targetHours) break;
                
                // Skip if not today and we've filled enough
                if (isToday && currentTime.isAfter(LocalTime.now(zone).minusMinutes(15))) {
                    break; // Don't fill future slots for today
                }
                
                int durationMinutes = 15 * (1 + random.nextInt(8)); // 15-120 min
                double durationHours = durationMinutes / 60.0;
                
                if (accumulatedHours + durationHours > targetHours) {
                    durationHours = targetHours - accumulatedHours;
                    durationMinutes = (int) (durationHours * 60);
                    durationMinutes = (durationMinutes / 15) * 15; // round to 15 min
                }
                
                if (durationMinutes < 15) break;
                
                LocalDateTime start = date.atTime(currentTime);
                LocalDateTime end = start.plusMinutes(durationMinutes);
                
                timeEntryRepository.save(TimeEntry.builder()
                        .user(user)
                        .delo(delo)
                        .startAt(start)
                        .endAt(end)
                        .status(isToday ? TimeEntry.Status.PLANNED : TimeEntry.Status.DONE)
                        .build());
                
                currentTime = end.toLocalTime().plusMinutes(15); // 15 min gap
                accumulatedHours += durationHours;
            }
        }
    }

    private void generateWeekPlans(User user, List<Project> projects, LocalDate today, Random random) {
        WeekFields wf = WeekFields.ISO;
        
        // Current week
        LocalDate weekStart = today.with(wf.dayOfWeek(), 1); // Monday
        for (Project project : projects) {
            if (project.getTotalPlanHours() != null && project.getTotalPlanHours().compareTo(BigDecimal.ZERO) > 0) {
                // Distribute plan across week days
                for (int d = 0; d < 7; d++) {
                    LocalDate day = weekStart.plusDays(d);
                    double dayPlan = project.getTotalPlanHours().doubleValue() / 5.0; // 5 work days
                    if (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
                        dayPlan *= 0.3;
                    }
                    if (dayPlan > 0.25) {
                        // In real implementation, this would create WeekPlanEntry
                        // For now we just ensure goals have weekly budgets
                    }
                }
            }
        }
        
        // Next week
        weekStart = weekStart.plusWeeks(1);
        // Same logic for next week
    }

    private void generateGoals(User user, List<Project> projects, LocalDate today, ZoneId zone, Random random) {
        Goal goal1 = Goal.builder()
                .user(user)
                .title("Запустить MVP продукта")
                .description("Доступный к продакшену MVP к концу квартала")
                .priority(1)
                .archived(false)
                .build();
        goal1 = goalRepository.save(goal1);
        
        Goal goal2 = Goal.builder()
                .user(user)
                .title("Научиться Java Spring Boot")
                .description("Пройти курс и написать пет-проект")
                .priority(2)
                .archived(false)
                .build();
        goal2 = goalRepository.save(goal2);
        
        Goal goal3 = Goal.builder()
                .user(user)
                .title("Улучшить физическую форму")
                .description("Бегать 3 раза в неделю, силовые 2 раза")
                .priority(3)
                .archived(false)
                .build();
        goal3 = goalRepository.save(goal3);
        
        // Link goals to projects
        GoalProject gp1 = new GoalProject();
        gp1.setId(new GoalProjectId(goal1.getId(), projects.get(0).getId()));
        gp1.setGoal(goal1);
        gp1.setProject(projects.get(0));
        goalProjectRepository.save(gp1);
        
        GoalProject gp2 = new GoalProject();
        gp2.setId(new GoalProjectId(goal1.getId(), projects.get(1).getId()));
        gp2.setGoal(goal1);
        gp2.setProject(projects.get(1));
        goalProjectRepository.save(gp2);
        
        GoalProject gp3 = new GoalProject();
        gp3.setId(new GoalProjectId(goal2.getId(), projects.get(2).getId()));
        gp3.setGoal(goal2);
        gp3.setProject(projects.get(2));
        goalProjectRepository.save(gp3);
        
        GoalProject gp4 = new GoalProject();
        gp4.setId(new GoalProjectId(goal3.getId(), projects.get(3).getId()));
        gp4.setGoal(goal3);
        gp4.setProject(projects.get(3));
        goalProjectRepository.save(gp4);
        
        // Weekly budgets for current and next 3 weeks
        WeekFields wf = WeekFields.ISO;
        for (int w = 0; w < 4; w++) {
            LocalDate weekStart = today.with(wf.dayOfWeek(), 1).plusWeeks(w);
            int isoYear = weekStart.get(wf.weekBasedYear());
            int isoWeek = weekStart.get(wf.weekOfWeekBasedYear());
            
            goalWeekBudgetRepository.save(GoalWeekBudget.builder()
                    .goal(goal1)
                    .isoYear(isoYear)
                    .isoWeek(isoWeek)
                    .hours(BigDecimal.valueOf(10 + random.nextInt(5)))
                    .build());
            goalWeekBudgetRepository.save(GoalWeekBudget.builder()
                    .goal(goal2)
                    .isoYear(isoYear)
                    .isoWeek(isoWeek)
                    .hours(BigDecimal.valueOf(5 + random.nextInt(3)))
                    .build());
            goalWeekBudgetRepository.save(GoalWeekBudget.builder()
                    .goal(goal3)
                    .isoYear(isoYear)
                    .isoWeek(isoWeek)
                    .hours(BigDecimal.valueOf(3 + random.nextInt(2)))
                    .build());
        }
        
        // Metrics
        goalMetricRepository.save(GoalMetric.builder()
                .goal(goal1)
                .kind("MILESTONE")
                .value(BigDecimal.ZERO)
                .targetValue(BigDecimal.ONE)
                .at(LocalDateTime.now())
                .build());
        goalMetricRepository.save(GoalMetric.builder()
                .goal(goal2)
                .kind("PROGRESS")
                .value(BigDecimal.valueOf(30))
                .targetValue(BigDecimal.valueOf(100))
                .at(LocalDateTime.now())
                .build());
        goalMetricRepository.save(GoalMetric.builder()
                .goal(goal3)
                .kind("COUNT")
                .value(BigDecimal.valueOf(12))
                .targetValue(BigDecimal.valueOf(20))
                .at(LocalDateTime.now())
                .build());
    }

    private void generateSynergies(User user, List<Project> projects, List<LifeSphere> spheres, Random random) {
        List<Synergy.Impact> impacts = List.of(Synergy.Impact.POSITIVE, Synergy.Impact.NEUTRAL, Synergy.Impact.NEGATIVE);
        
        // Project -> Sphere synergies
        for (Project project : projects) {
            if (random.nextBoolean()) {
                LifeSphere sphere = spheres.get(random.nextInt(spheres.size()));
                if (!synergyRepository.existsByUserAndProjectAndSphere(user, project, sphere)) {
                    synergyRepository.save(Synergy.builder()
                            .user(user)
                            .project(project)
                            .sphere(sphere)
                            .impact(impacts.get(random.nextInt(impacts.size())))
                            .build());
                }
            }
        }
        
        // Additional synergies
        for (int i = 0; i < 2; i++) {
            Project project = projects.get(random.nextInt(projects.size()));
            LifeSphere sphere = spheres.get(random.nextInt(spheres.size()));
            if (!synergyRepository.existsByUserAndProjectAndSphere(user, project, sphere)) {
                synergyRepository.save(Synergy.builder()
                        .user(user)
                        .project(project)
                        .sphere(sphere)
                        .impact(impacts.get(random.nextInt(impacts.size())))
                        .build());
            }
        }
    }

    private void generateIdeas(User user, LocalDate today, Random random) {
        String[] ideaTitles = {
                "Автоматизация деплоя через GitHub Actions",
                "Внедрение интеграционных тестов",
                "Миграция на Java 21 LTS",
                "Добавление темной темы в UI"
        };
        Idea.Status[] statuses = {Idea.Status.BANK, Idea.Status.IN_WORK, Idea.Status.IN_WORK, Idea.Status.ARCHIVED};
        
        for (int i = 0; i < 4; i++) {
            Idea idea = Idea.builder()
                    .user(user)
                    .title(ideaTitles[i])
                    .description("Идея для улучшения: " + ideaTitles[i].toLowerCase())
                    .category(Idea.Category.PERSONAL)
                    .status(statuses[i])
                    .build();
            ideaRepository.save(idea);
        }
    }

    private void generateNotes(User user, List<Project> projects, List<Delo> delos, LocalDate today, Random random) {
        // User note
        noteRepository.save(Note.builder()
                .user(user)
                .project(projects.get(0))
                .author(Note.Author.USER)
                .body("Важное напоминание: согласовать макеты с дизайнером до пятницы")
                .tags(new String[]{"важное", "дизайн"})
                .build());
        
        // Agent note
        noteRepository.save(Note.builder()
                .user(user)
                .project(projects.get(1))
                .author(Note.Author.AGENT)
                .body("Агент предложил: рассмотреть кэширование ответа API для ускорения отдачи списка проектов")
                .tags(new String[]{"агент", "оптимизация"})
                .build());
        
        // Deli-bound note
        Delo delo = delos.get(random.nextInt(delos.size()));
        noteRepository.save(Note.builder()
                .user(user)
                .delo(delo)
                .author(Note.Author.USER)
                .body("Идея на будущее: добавить фильтр по тегам в бэклог")
                .tags(new String[]{"идея", "бэклог"})
                .build());
    }

    private void generateProjectDependencies(User user, List<Project> projects) {
        if (projects.size() >= 2) {
            // API development blocks redesign deployment
            projectDependencyRepository.save(ProjectDependency.builder()
                    .user(user)
                    .blocker(projects.get(1))
                    .blocked(projects.get(0))
                    .build());
        }
    }

    private void generateBacklogItems(User user, List<Delo> delos, LocalDate today, Random random) {
        WeekFields wf = WeekFields.ISO;
        String currentWeek = today.get(wf.weekBasedYear()) + "-W" + String.format("%02d", today.get(wf.weekOfWeekBasedYear()));
        
        for (int i = 0; i < 5; i++) {
            Delo delo = delos.get(random.nextInt(delos.size()));
            if (backlogItemRepository.findByUserAndDeloIdAndScopeAndPeriodId(user, delo.getId(), BacklogItem.Scope.WEEK, currentWeek).isEmpty()) {
                backlogItemRepository.save(BacklogItem.builder()
                        .user(user)
                        .delo(delo)
                        .scope(BacklogItem.Scope.WEEK)
                        .periodId(currentWeek)
                        .plannedHours(BigDecimal.valueOf(0.5 + random.nextDouble() * 2.0))
                        .position(i)
                        .build());
            }
        }
    }

    private void generateRoutines(User user, List<LifeArea> areas, LocalDate today, ZoneId zone, Random random) {
        Routine running = Routine.builder()
                .user(user)
                .title("Утренняя пробежка")
                .description("Легкий бег 30-40 мин")
                .weeklyHours(BigDecimal.valueOf(2.5))
                .color("#EF4444")
                .icon("🏃")
                .archived(false)
                .build();
        running = routineRepository.save(running);
        
        routineScheduleRepository.save(RoutineSchedule.builder()
                .routine(running)
                .dayOfWeek(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(7, 0))
                .endTime(LocalTime.of(7, 45))
                .build());
        routineScheduleRepository.save(RoutineSchedule.builder()
                .routine(running)
                .dayOfWeek(DayOfWeek.WEDNESDAY)
                .startTime(LocalTime.of(7, 0))
                .endTime(LocalTime.of(7, 45))
                .build());
        routineScheduleRepository.save(RoutineSchedule.builder()
                .routine(running)
                .dayOfWeek(DayOfWeek.FRIDAY)
                .startTime(LocalTime.of(7, 0))
                .endTime(LocalTime.of(7, 45))
                .build());
        
        Routine learning = Routine.builder()
                .user(user)
                .title("Изучение Java")
                .description("Курс + практика")
                .weeklyHours(BigDecimal.valueOf(4.0))
                .color("#3B82F6")
                .icon("☕")
                .archived(false)
                .build();
        learning = routineRepository.save(learning);
        
        routineScheduleRepository.save(RoutineSchedule.builder()
                .routine(learning)
                .dayOfWeek(DayOfWeek.TUESDAY)
                .startTime(LocalTime.of(19, 0))
                .endTime(LocalTime.of(21, 0))
                .build());
        routineScheduleRepository.save(RoutineSchedule.builder()
                .routine(learning)
                .dayOfWeek(DayOfWeek.THURSDAY)
                .startTime(LocalTime.of(19, 0))
                .endTime(LocalTime.of(21, 0))
                .build());
        routineScheduleRepository.save(RoutineSchedule.builder()
                .routine(learning)
                .dayOfWeek(DayOfWeek.SATURDAY)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(12, 0))
                .build());
    }
}