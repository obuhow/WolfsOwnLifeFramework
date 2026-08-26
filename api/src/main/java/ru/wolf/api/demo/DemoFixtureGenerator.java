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
package ru.wolf.api.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.goal.Goal;
import ru.wolf.api.goal.GoalMetric;
import ru.wolf.api.goal.GoalMetricRepository;
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
import ru.wolf.api.backlog.BacklogItem;
import ru.wolf.api.backlog.BacklogItemRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Generates deterministic, realistic demo fixtures for any user from a declarative
 * {@link DemoProfile} (see {@code assets/profiles/<slug>.json}). All dates are relative
 * to "today" in the profile/user timezone at the moment of loading.
 * Deterministic where the profile does not specify an exact value: uses a fixed seed
 * based on user ID for reproducible results.
 */
@Component
@RequiredArgsConstructor
public class DemoFixtureGenerator {

    private static final String[] AREA_COLORS = {"#3B82F6", "#10B981", "#8B5CF6", "#F59E0B", "#EC4899", "#06B6D4"};
    private static final String[] ROUTINE_COLORS = {"#EF4444", "#3B82F6", "#10B981", "#8B5CF6", "#F59E0B"};
    private static final int DEFAULT_HISTORY_DAYS = 30;

    private final LifeSphereSeeder lifeSphereSeeder;
    private final LifeAreaRepository lifeAreaRepository;
    private final LifeSphereRepository lifeSphereRepository;
    private final ProjectRepository projectRepository;
    private final DeloRepository deloRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final GoalRepository goalRepository;
    private final GoalMetricRepository goalMetricRepository;
    private final GoalWeekBudgetRepository goalWeekBudgetRepository;
    private final IdeaRepository ideaRepository;
    private final NoteRepository noteRepository;
    private final SynergyRepository synergyRepository;
    private final ProjectDependencyRepository projectDependencyRepository;
    private final RoutineRepository routineRepository;
    private final RoutineScheduleRepository routineScheduleRepository;
    private final BacklogItemRepository backlogItemRepository;

    /**
     * Populates the given user with a complete demo dataset described by {@code profile}.
     * Idempotent on empty user (fails if user already has data).
     */
    @Transactional
    public void populate(User user, DemoProfile profile) {
        // Check if user already has data
        if (!projectRepository.findByUserOrderByTitleAsc(user).isEmpty()) {
            throw new DemoDataConflictException("У пользователя уже есть данные — загрузка профиля отклонена");
        }
        if (!deloRepository.findByUserOrderByTitleAsc(user).isEmpty()) {
            throw new DemoDataConflictException("У пользователя уже есть Дела — загрузка профиля отклонена");
        }
        if (!timeEntryRepository.findOverlapping(user.getId(),
                LocalDateTime.now().minusDays(14), LocalDateTime.now().plusDays(1)).isEmpty()) {
            throw new DemoDataConflictException("У пользователя уже есть Записи времени — загрузка профиля отклонена");
        }

        // Use deterministic random based on user ID, for values the profile does not specify
        Random random = new Random(user.getId() ^ 0x5A5A5A5AL);

        ZoneId zone = ZoneId.of(profile.timezone() != null ? profile.timezone() : user.getTimezone());
        LocalDate today = LocalDate.now(zone);

        // 1. Seed 9 Life Spheres (canonical, independent of the profile)
        lifeSphereSeeder.seed(user);
        List<LifeSphere> spheres = lifeSphereRepository.findByUserOrderBySortOrderAscNameAsc(user);

        // 2. Life Areas from the profile
        Map<String, LifeArea> areasByName = new LinkedHashMap<>();
        Map<String, String> sphereLabelByAreaName = new HashMap<>();
        int sortOrder = 0;
        for (DemoProfile.Area a : safe(profile.areas())) {
            LifeArea area = createLifeArea(user, a.name(), sortOrder, AREA_COLORS[sortOrder % AREA_COLORS.length]);
            sortOrder++;
            areasByName.put(a.name(), area);
            sphereLabelByAreaName.put(a.name(), a.sphere());
        }

        // 3. Projects from the profile
        Map<String, Project> projectsByName = new LinkedHashMap<>();
        for (DemoProfile.ProfileProject p : safe(profile.projects())) {
            LifeArea area = areasByName.get(p.area());
            if (area == null) {
                throw new IllegalStateException("Профиль ссылается на несуществующую область: " + p.area());
            }
            LocalDate start = p.startOffsetDays() != null ? today.plusDays(p.startOffsetDays()) : null;
            LocalDate due = p.dueOffsetDays() != null ? today.plusDays(p.dueOffsetDays()) : null;
            Project.Status status = "ARCHIVED".equalsIgnoreCase(p.status()) ? Project.Status.ARCHIVED : Project.Status.IN_PROGRESS;
            Project project = createProject(user, area, p.name(), status, start, due, p.plannedHours());
            projectsByName.put(p.name(), project);
        }
        List<Project> projects = new ArrayList<>(projectsByName.values());

        // 4. Delos from delosSample — created without a Project link (matches the declarative schema)
        DemoProfile.DelosSample sample = profile.delosSample();
        List<Delo> doneDelos = createDelos(user, sample != null ? sample.done() : null);
        List<Delo> plannedDelos = createDelos(user, sample != null ? sample.planned() : null);
        List<Delo> backlogDelos = createDelos(user, sample != null ? sample.backlog() : null);
        List<Delo> allDelos = new ArrayList<>();
        allDelos.addAll(doneDelos);
        allDelos.addAll(plannedDelos);
        allDelos.addAll(backlogDelos);

        // 5. Time entries: history window ending today (today filled partially)
        int historyDays = profile.timeEntriesHistoryDays() != null ? profile.timeEntriesHistoryDays() : DEFAULT_HISTORY_DAYS;
        generateTimeEntries(user, doneDelos, plannedDelos, today, zone, random, historyDays);

        // 6. Goals with weekly budgets and an optional metric
        generateGoals(user, profile.goals(), today, random);

        // 7. Synergies: Project -> LifeSphere, resolved via the profile's area->sphere labels
        generateSynergies(user, profile.synergy(), projectsByName, sphereLabelByAreaName, spheres, random);

        // 8. Ideas (Банк идей)
        generateIdeas(user, profile.ideas());

        // 9. Notes (at least one фromAgent=true per profile content)
        generateNotes(user, profile.notes(), projects, allDelos, random);

        // 10. Project dependency (at least 1 when >=2 projects)
        generateProjectDependencies(user, projects);

        // 11. Backlog items — one per backlog Дело
        generateBacklogItems(user, backlogDelos, today, random);

        // 12. Routines (Рутины) with weekly schedules parsed from the profile's rrule
        generateRoutines(user, profile.routines(), random);
    }

    private <T> List<T> safe(List<T> list) {
        return list != null ? list : List.of();
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

    private Project createProject(User user, LifeArea lifeArea, String title, Project.Status status,
                                  LocalDate startDate, LocalDate endDate, BigDecimal totalPlanHours) {
        Project project = Project.builder()
                .user(user)
                .lifeArea(lifeArea)
                .title(title)
                .status(status)
                .description("Демо-проект: " + title)
                .startDate(startDate)
                .endDate(endDate)
                .totalPlanHours(totalPlanHours)
                .planDistribution(totalPlanHours != null ? Project.PlanDistribution.EVEN_WEEKDAYS : Project.PlanDistribution.NONE)
                .planFrozenAt(LocalDate.now().with(java.time.temporal.TemporalAdjusters.firstDayOfMonth()))
                .build();
        return projectRepository.save(project);
    }

    private List<Delo> createDelos(User user, List<String> titles) {
        List<Delo> out = new ArrayList<>();
        for (String title : safe(titles)) {
            out.add(deloRepository.save(Delo.builder()
                    .user(user)
                    .title(title)
                    .executionMode(Delo.ExecutionMode.SELF)
                    .build()));
        }
        return out;
    }

    private Delo getOrCreateSleepDelo(User user) {
        return deloRepository.findFirstByUserAndTitleIgnoreCaseOrderByIdAsc(user, "Сон")
                .orElseGet(() -> deloRepository.save(Delo.builder()
                        .user(user)
                        .title("Сон")
                        .description("Системное Дело для автозаполнения сна")
                        .executionMode(Delo.ExecutionMode.SELF)
                        .build()));
    }

    private void generateTimeEntries(User user, List<Delo> doneDelos, List<Delo> plannedDelos,
                                     LocalDate today, ZoneId zone, Random random, int historyDays) {
        LocalTime nightStart = user.getNightStart();
        LocalTime defaultSleepEnd = user.getDefaultSleepEnd();

        // Get or create sleep delo
        Delo sleepDelo = getOrCreateSleepDelo(user);

        List<Delo> pastPool = !doneDelos.isEmpty() ? doneDelos : plannedDelos;
        List<Delo> todayPool = !plannedDelos.isEmpty() ? plannedDelos : doneDelos;

        for (int dayOffset = -(historyDays - 1); dayOffset <= 0; dayOffset++) {
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
                    .delo(sleepDelo)
                    .startAt(sleepStart)
                    .endAt(sleepEnd)
                    .status(TimeEntry.Status.DONE)
                    .build());

            List<Delo> pool = isToday ? todayPool : pastPool;
            if (pool.isEmpty()) {
                continue;
            }

            // Shuffle deterministically
            List<Delo> availableDelos = new ArrayList<>(pool);
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

    private void generateGoals(User user, List<DemoProfile.Goal> goals, LocalDate today, Random random) {
        if (goals == null || goals.isEmpty()) {
            return;
        }
        WeekFields wf = WeekFields.ISO;
        int priority = 1;
        for (DemoProfile.Goal g : goals) {
            Goal goal = Goal.builder()
                    .user(user)
                    .title(g.name())
                    .priority(priority++)
                    .archived(false)
                    .build();
            goal = goalRepository.save(goal);

            BigDecimal weeklyHours = g.weeklyBudgetHours() != null ? g.weeklyBudgetHours() : BigDecimal.valueOf(5);
            for (int w = 0; w < 4; w++) {
                LocalDate weekStart = today.with(wf.dayOfWeek(), 1).plusWeeks(w);
                int isoYear = weekStart.get(wf.weekBasedYear());
                int isoWeek = weekStart.get(wf.weekOfWeekBasedYear());
                goalWeekBudgetRepository.save(GoalWeekBudget.builder()
                        .goal(goal)
                        .isoYear(isoYear)
                        .isoWeek(isoWeek)
                        .hours(weeklyHours.setScale(2, RoundingMode.HALF_UP))
                        .build());
            }

            if (g.metric() != null && !g.metric().isBlank()) {
                goalMetricRepository.save(GoalMetric.builder()
                        .goal(goal)
                        .kind(g.metric())
                        .value(BigDecimal.ZERO)
                        .targetValue(BigDecimal.valueOf(100))
                        .at(LocalDateTime.now())
                        .build());
            }
        }
    }

    private void generateSynergies(User user, List<DemoProfile.Synergy> synergyEntries,
                                   Map<String, Project> projectsByName, Map<String, String> sphereLabelByAreaName,
                                   List<LifeSphere> spheres, Random random) {
        if (synergyEntries == null || spheres.isEmpty()) {
            return;
        }
        for (DemoProfile.Synergy s : synergyEntries) {
            Project project = projectsByName.get(s.project());
            if (project == null) {
                continue; // profile referenced an unknown project name — skip defensively
            }
            String desiredSphereName = sphereLabelByAreaName.get(s.area());
            LifeSphere sphere = resolveSphere(desiredSphereName, spheres, random);
            if (synergyRepository.existsByUserAndProjectAndSphere(user, project, sphere)) {
                continue;
            }
            synergyRepository.save(Synergy.builder()
                    .user(user)
                    .project(project)
                    .sphere(sphere)
                    .impact(mapWeightToImpact(s.weight()))
                    .build());
        }
    }

    /**
     * The profile's area {@code sphere} label is descriptive content, not guaranteed to match
     * one of the 9 canonical seeded Сферы жизни. Resolve by exact name when possible; otherwise
     * fall back to a deterministic pick so Synergy stays non-empty.
     */
    private LifeSphere resolveSphere(String desiredName, List<LifeSphere> spheres, Random random) {
        if (desiredName != null) {
            for (LifeSphere sp : spheres) {
                if (sp.getName().equalsIgnoreCase(desiredName)) {
                    return sp;
                }
            }
        }
        int idx = Math.floorMod(desiredName != null ? desiredName.hashCode() : random.nextInt(), spheres.size());
        return spheres.get(idx);
    }

    private Synergy.Impact mapWeightToImpact(String weight) {
        if (weight == null) {
            return Synergy.Impact.NEUTRAL;
        }
        return switch (weight.toLowerCase(Locale.ROOT)) {
            case "high", "medium" -> Synergy.Impact.POSITIVE;
            default -> Synergy.Impact.NEUTRAL;
        };
    }

    private void generateIdeas(User user, List<String> ideas) {
        if (ideas == null || ideas.isEmpty()) {
            return;
        }
        Idea.Status[] cycle = {Idea.Status.BANK, Idea.Status.IN_WORK, Idea.Status.ARCHIVED};
        for (int i = 0; i < ideas.size(); i++) {
            String title = ideas.get(i);
            Idea idea = Idea.builder()
                    .user(user)
                    .title(title)
                    .description("Идея: " + title.toLowerCase(Locale.ROOT))
                    .category(Idea.Category.PERSONAL)
                    .status(cycle[i % cycle.length])
                    .build();
            ideaRepository.save(idea);
        }
    }

    private void generateNotes(User user, List<DemoProfile.Note> notes, List<Project> projects, List<Delo> delos, Random random) {
        if (notes == null || notes.isEmpty()) {
            return;
        }
        int projectIdx = 0;
        for (DemoProfile.Note n : notes) {
            Note.Author author = Boolean.TRUE.equals(n.fromAgent()) ? Note.Author.AGENT : Note.Author.USER;
            String body = n.title() != null && !n.title().isBlank() ? n.title() + ". " + n.body() : n.body();
            Note.NoteBuilder builder = Note.builder()
                    .user(user)
                    .author(author)
                    .body(body)
                    .tags(author == Note.Author.AGENT ? new String[]{"агент"} : new String[0]);

            if (!projects.isEmpty()) {
                builder.project(projects.get(projectIdx % projects.size()));
                projectIdx++;
            } else if (!delos.isEmpty()) {
                builder.delo(delos.get(random.nextInt(delos.size())));
            } else {
                continue; // cannot satisfy the exactly-one-parent constraint
            }
            noteRepository.save(builder.build());
        }
    }

    private void generateProjectDependencies(User user, List<Project> projects) {
        if (projects.size() >= 2) {
            projectDependencyRepository.save(ProjectDependency.builder()
                    .user(user)
                    .blocker(projects.get(1))
                    .blocked(projects.get(0))
                    .build());
        }
    }

    private void generateBacklogItems(User user, List<Delo> backlogDelos, LocalDate today, Random random) {
        if (backlogDelos.isEmpty()) {
            return;
        }
        WeekFields wf = WeekFields.ISO;
        String currentWeek = today.get(wf.weekBasedYear()) + "-W" + String.format("%02d", today.get(wf.weekOfWeekBasedYear()));

        int position = 0;
        for (Delo delo : backlogDelos) {
            if (backlogItemRepository.findByUserAndDeloIdAndScopeAndPeriodId(user, delo.getId(), BacklogItem.Scope.WEEK, currentWeek).isEmpty()) {
                backlogItemRepository.save(BacklogItem.builder()
                        .user(user)
                        .delo(delo)
                        .scope(BacklogItem.Scope.WEEK)
                        .periodId(currentWeek)
                        .plannedHours(BigDecimal.valueOf(0.5 + random.nextDouble() * 2.0))
                        .position(position++)
                        .build());
            }
        }
    }

    private void generateRoutines(User user, List<DemoProfile.Routine> routines, Random random) {
        if (routines == null || routines.isEmpty()) {
            return;
        }
        int idx = 0;
        for (DemoProfile.Routine r : routines) {
            List<DayOfWeek> weekdays = parseByDay(r.rrule());
            int minutes = r.durationMinutes() != null ? r.durationMinutes() : 60;
            BigDecimal weeklyHours = BigDecimal.valueOf(weekdays.size() * minutes / 60.0).setScale(2, RoundingMode.HALF_UP);

            Routine routine = Routine.builder()
                    .user(user)
                    .title(r.name())
                    .weeklyHours(weeklyHours)
                    .color(ROUTINE_COLORS[idx % ROUTINE_COLORS.length])
                    .archived(false)
                    .build();
            routine = routineRepository.save(routine);

            LocalTime start = LocalTime.of(19, 0);
            LocalTime end = start.plusMinutes(minutes);
            for (DayOfWeek day : weekdays) {
                routineScheduleRepository.save(RoutineSchedule.builder()
                        .routine(routine)
                        .dayOfWeek(day)
                        .startTime(start)
                        .endTime(end)
                        .build());
            }
            idx++;
        }
    }

    /** Minimal RFC5545-style BYDAY parser: {@code "FREQ=WEEKLY;BYDAY=TU,TH"} -> [TUESDAY, THURSDAY]. */
    private List<DayOfWeek> parseByDay(String rrule) {
        if (rrule == null || rrule.isBlank()) {
            return List.of();
        }
        Map<String, String> parts = new HashMap<>();
        for (String token : rrule.split(";")) {
            String[] kv = token.split("=", 2);
            if (kv.length == 2) {
                parts.put(kv[0].trim().toUpperCase(Locale.ROOT), kv[1].trim());
            }
        }
        String byday = parts.get("BYDAY");
        if (byday == null || byday.isBlank()) {
            return List.of();
        }
        List<DayOfWeek> days = new ArrayList<>();
        for (String code : byday.split(",")) {
            DayOfWeek day = mapByDayCode(code.trim().toUpperCase(Locale.ROOT));
            if (day != null) {
                days.add(day);
            }
        }
        return days;
    }

    private DayOfWeek mapByDayCode(String code) {
        return switch (code) {
            case "MO" -> DayOfWeek.MONDAY;
            case "TU" -> DayOfWeek.TUESDAY;
            case "WE" -> DayOfWeek.WEDNESDAY;
            case "TH" -> DayOfWeek.THURSDAY;
            case "FR" -> DayOfWeek.FRIDAY;
            case "SA" -> DayOfWeek.SATURDAY;
            case "SU" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }
}
