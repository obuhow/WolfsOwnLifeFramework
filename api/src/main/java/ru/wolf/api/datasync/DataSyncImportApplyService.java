package ru.wolf.api.datasync;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.backlog.BacklogItem;
import ru.wolf.api.backlog.BacklogItemRepository;
import ru.wolf.api.checklist.DailyChecklistItem;
import ru.wolf.api.checklist.DailyChecklistItemRepository;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloProject;
import ru.wolf.api.delo.DeloProjectId;
import ru.wolf.api.delo.DeloProjectRepository;
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
import ru.wolf.api.importxlsx.ActivityMapping;
import ru.wolf.api.importxlsx.ActivityMappingRepository;
import ru.wolf.api.lifearea.LifeArea;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.lifesphere.LifeSphere;
import ru.wolf.api.lifesphere.LifeSphereRepository;
import ru.wolf.api.lifesphere.Synergy;
import ru.wolf.api.lifesphere.SynergyRepository;
import ru.wolf.api.note.Note;
import ru.wolf.api.note.NoteAttachment;
import ru.wolf.api.note.NoteRepository;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectDependency;
import ru.wolf.api.project.ProjectDependencyId;
import ru.wolf.api.project.ProjectDependencyRepository;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.routine.Routine;
import ru.wolf.api.routine.RoutineGoal;
import ru.wolf.api.routine.RoutineGoalId;
import ru.wolf.api.routine.RoutineGoalRepository;
import ru.wolf.api.routine.RoutineRepository;
import ru.wolf.api.routine.RoutineSchedule;
import ru.wolf.api.routine.RoutineScheduleRepository;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DataSyncImportApplyService {
    private final DataSyncImportService importService;
    private final SyncImportPreviewRepository previews;
    private final SyncExternalIdRepository externalIds;
    private final LifeAreaRepository lifeAreas;
    private final LifeSphereRepository lifeSpheres;
    private final ProjectRepository projects;
    private final ProjectDependencyRepository projectDependencies;
    private final DeloRepository delos;
    private final DeloProjectRepository deloProjects;
    private final TimeEntryRepository timeEntries;
    private final RoutineRepository routines;
    private final RoutineScheduleRepository routineSchedules;
    private final RoutineGoalRepository routineGoals;
    private final GoalRepository goals;
    private final GoalMetricRepository goalMetrics;
    private final GoalWeekBudgetRepository goalWeekBudgets;
    private final GoalProjectRepository goalProjects;
    private final IdeaRepository ideas;
    private final NoteRepository notes;
    private final SynergyRepository synergies;
    private final BacklogItemRepository backlogItems;
    private final DailyChecklistItemRepository checklistItems;
    private final ActivityMappingRepository activityMappings;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public ApplyResponse apply(User user, Long previewId, String checksum, boolean deleteMissing,
                               List<String> scopes) throws Exception {
        SyncImportPreview preview = importService.find(user, previewId);
        if (preview.getAppliedAt() != null) {
            if (!preview.getChecksum().equals(checksum)) {
                throw new IllegalArgumentException("Checksum не совпадает с применённым preview");
            }
            return objectMapper.readValue(preview.getResultJson(), ApplyResponse.class);
        }
        if (!"VALID".equals(preview.getStatus()) || !preview.getChecksum().equals(checksum)) {
            throw new IllegalArgumentException("Apply разрешён только для актуального валидного preview с тем же checksum");
        }
        if (deleteMissing && (scopes == null || scopes.isEmpty())) {
            throw new IllegalArgumentException("deleteMissing требует явного списка scopes");
        }

        Map<String, Integer> created = new HashMap<>();
        Map<String, Integer> updated = new HashMap<>();
        Map<String, Integer> deleted = new HashMap<>();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(preview.getWorkbookData()))) {
            Map<String, LifeArea> areaMap = applyLifeAreas(user, workbook, created, updated);
            Map<String, LifeSphere> sphereMap = applyLifeSpheres(user, workbook, created, updated);
            Map<String, Project> projectMap = applyProjects(user, workbook, areaMap, created, updated);
            Map<String, Routine> routineMap = applyRoutines(user, workbook, created, updated);
            Map<String, Goal> goalMap = applyGoals(user, workbook, created, updated);
            Map<String, Delo> deloMap = applyDelos(user, workbook, projectMap, created, updated);
            Map<String, Idea> ideaMap = applyIdeas(user, workbook, projectMap, created, updated);
            applyRoutineSchedules(user, workbook, routineMap, created, updated);
            applyTimeEntries(user, workbook, deloMap, created, updated);
            applyGoalMetrics(user, workbook, goalMap, created, updated);
            applyGoalBudgets(user, workbook, goalMap, created, updated);

            applyNotes(user, workbook, projectMap, deloMap, created, updated);
            applySynergies(user, workbook, sphereMap, projectMap, routineMap, ideaMap, created, updated);
            applyProjectDependencies(user, workbook, projectMap, created, updated);
            applyBacklog(user, workbook, deloMap, created, updated);
            applyChecklist(user, workbook, deloMap, created, updated);
            applyActivityMappings(user, workbook, deloMap, created, updated);
            if (deleteMissing) deleteMissing(user, workbook, scopes, deleted);
        }

        ApplyResponse result = new ApplyResponse(previewId, created, updated, deleted, "APPLIED");
        preview.setAppliedAt(Instant.now());
        preview.setResultJson(objectMapper.writeValueAsString(result));
        preview.setStatus("APPLIED");
        previews.save(preview);
        return result;
    }

    private Map<String, LifeArea> applyLifeAreas(User user, Workbook workbook, Map<String, Integer> created, Map<String, Integer> updated) {
        Map<String, LifeArea> result = new HashMap<>();
        for (int i = 1; i <= lastRow(workbook, "life_areas"); i++) {
            Row row = row(workbook, "life_areas", i); String xid = text(row, 0);
            LifeArea area = existing(user, "life_area", xid, id -> lifeAreas.findByUserAndId(user, id).orElseThrow());
            boolean isNew = area == null;
            if (isNew) area = LifeArea.builder().user(user).build();
            area.setName(required(row, 1, "life_areas", i));
            area.setSortOrder(integer(row, 2, 0)); area.setColor(nullable(row, 3));
            area = lifeAreas.save(area); bind(user, "life_area", area.getId(), xid);
            result.put(xid, area); count(isNew, "life_areas", created, updated);
        }
        return result;
    }

    private Map<String, LifeSphere> applyLifeSpheres(User user, Workbook workbook, Map<String, Integer> created, Map<String, Integer> updated) {
        Map<String, LifeSphere> result = new HashMap<>();
        for (int i = 1; i <= lastRow(workbook, "life_spheres"); i++) {
            Row row = row(workbook, "life_spheres", i); String xid = text(row, 0);
            LifeSphere sphere = existing(user, "life_sphere", xid, id -> lifeSpheres.findByUserAndId(user, id).orElseThrow());
            boolean isNew = sphere == null;
            if (isNew) sphere = LifeSphere.builder().user(user).build();
            sphere.setName(required(row, 1, "life_spheres", i)); sphere.setSortOrder(integer(row, 2, 0));
            sphere.setColor(nullable(row, 3)); sphere.setArchived(bool(row, 4, false));
            sphere = lifeSpheres.save(sphere); bind(user, "life_sphere", sphere.getId(), xid);
            result.put(xid, sphere); count(isNew, "life_spheres", created, updated);
        }
        return result;
    }

    private Map<String, Project> applyProjects(User user, Workbook workbook, Map<String, LifeArea> areas, Map<String, Integer> created, Map<String, Integer> updated) {
        Map<String, Project> result = new HashMap<>();
        for (int i = 1; i <= lastRow(workbook, "projects"); i++) {
            Row row = row(workbook, "projects", i); String xid = text(row, 0);
            LifeArea area = require(areas, text(row, 1), "Неизвестная Область жизни: " + text(row, 1));
            Project project = existing(user, "project", xid, id -> projects.findByUserAndId(user, id).orElseThrow());
            boolean isNew = project == null;
            if (isNew) project = Project.builder().user(user).lifeArea(area).build();
            project.setLifeArea(area); project.setTitle(required(row, 3, "projects", i));
            project.setStatus(enumValue(row, 4, Project.Status.class, "projects", i)); project.setDescription(nullable(row, 5));
            project.setStartDate(date(row, 6)); project.setEndDate(date(row, 7)); project.setTotalPlanHours(decimal(row, 8));
            project.setPlanDistribution(enumValue(row, 9, Project.PlanDistribution.class, "projects", i)); project.setPlanFrozenAt(date(row, 10));
            project = projects.save(project); bind(user, "project", project.getId(), xid); result.put(xid, project); count(isNew, "projects", created, updated);
        }
        for (int i = 1; i <= lastRow(workbook, "projects"); i++) {
            Row row = row(workbook, "projects", i); Project project = result.get(text(row, 0));
            project.setParent(nullable(row, 2).isBlank() ? null : require(result, text(row, 2), "Неизвестный parentExternalId: " + text(row, 2)));
            projects.save(project);
        }
        return result;
    }

    private Map<String, Routine> applyRoutines(User user, Workbook workbook, Map<String, Integer> created, Map<String, Integer> updated) {
        Map<String, Routine> result = new HashMap<>();
        for (int i = 1; i <= lastRow(workbook, "routines"); i++) {
            Row row = row(workbook, "routines", i); String xid = text(row, 0);
            Routine routine = existing(user, "routine", xid, id -> routines.findByUserAndId(user, id).orElseThrow());
            boolean isNew = routine == null;
            if (isNew) routine = Routine.builder().user(user).build();
            routine.setTitle(required(row, 1, "routines", i)); routine.setDescription(nullable(row, 2));
            routine.setWeeklyHours(requiredDecimalValue(row, 3, "routines", i)); routine.setColor(nullable(row, 4)); routine.setIcon(nullable(row, 5)); routine.setArchived(bool(row, 6, false));
            routine = routines.save(routine); bind(user, "routine", routine.getId(), xid); result.put(xid, routine); count(isNew, "routines", created, updated);
        }
        return result;
    }

    private Map<String, Goal> applyGoals(User user, Workbook workbook, Map<String, Integer> created, Map<String, Integer> updated) {
        Map<String, Goal> result = new HashMap<>();
        for (int i = 1; i <= lastRow(workbook, "goals"); i++) {
            Row row = row(workbook, "goals", i); String xid = text(row, 0);
            Goal goal = existing(user, "goal", xid, id -> goals.findByUserAndId(user, id).orElseThrow());
            boolean isNew = goal == null;
            if (isNew) goal = Goal.builder().user(user).build();
            goal.setTitle(required(row, 1, "goals", i)); goal.setDescription(nullable(row, 2)); goal.setPriority(integerRequired(row, 3, "goals", i)); goal.setArchived(bool(row, 4, false));
            goal = goals.save(goal); bind(user, "goal", goal.getId(), xid); result.put(xid, goal); count(isNew, "goals", created, updated);
        }
        return result;
    }

    private Map<String, Delo> applyDelos(User user, Workbook workbook, Map<String, Project> projects, Map<String, Integer> created, Map<String, Integer> updated) {
        Map<String, Delo> result = new HashMap<>();
        for (int i = 1; i <= lastRow(workbook, "delos"); i++) {
            Row row = row(workbook, "delos", i); String xid = text(row, 0);
            Delo delo = existing(user, "delo", xid, id -> delos.findByUserAndId(user, id).orElseThrow());
            boolean isNew = delo == null;
            if (isNew) delo = Delo.builder().user(user).build();
            delo.setTitle(required(row, 1, "delos", i)); delo.setDescription(nullable(row, 2)); delo.setExecutionMode(enumValue(row, 3, Delo.ExecutionMode.class, "delos", i));
            delo.setRecurrenceWeekdays(nullable(row, 4)); delo.setRecurrenceWindowStart(time(row, 5)); delo.setRecurrenceWindowEnd(time(row, 6)); delo.setRecurrenceSlots(nullable(row, 7));
            delo = delos.save(delo); bind(user, "delo", delo.getId(), xid); result.put(xid, delo); count(isNew, "delos", created, updated);
            Set<String> projectIds = pipe(row, 8); String primary = nullable(row, 9);
            if (!primary.isBlank() && !projectIds.contains(primary)) throw rowError("delos", i, "primaryProjectExternalId должен входить в projectExternalIds");
            Set<Long> incomingProjectIds = new HashSet<>();
            for (String projectId : projectIds) {
                Project project = require(projects, projectId, "Неизвестный projectExternalId: " + projectId);
                incomingProjectIds.add(project.getId());
                DeloProject link = deloProjects.findById(new DeloProjectId(delo.getId(), project.getId())).orElse(null);
                if (link == null) link = DeloProject.builder().id(new DeloProjectId(delo.getId(), project.getId())).delo(delo).project(project).build();
                link.setIsPrimary(projectId.equals(primary));
                deloProjects.save(link);
            }
            for (DeloProject link : deloProjects.findByDeloId(delo.getId())) if (!incomingProjectIds.contains(link.getProject().getId())) deloProjects.delete(link);
        }
        return result;
    }

    private Map<String, Idea> applyIdeas(User user, Workbook workbook, Map<String, Project> projects, Map<String, Integer> created, Map<String, Integer> updated) {
        Map<String, Idea> result = new HashMap<>();
        for (int i = 1; i <= lastRow(workbook, "ideas"); i++) {
            Row row = row(workbook, "ideas", i); String xid = text(row, 0);
            Idea idea = existing(user, "idea", xid, id -> ideas.findByUserAndId(user, id).orElseThrow());
            boolean isNew = idea == null;
            if (isNew) idea = Idea.builder().user(user).build();
            idea.setTitle(required(row, 1, "ideas", i)); idea.setDescription(nullable(row, 2)); idea.setCategory(enumValue(row, 3, Idea.Category.class, "ideas", i)); idea.setStatus(enumValue(row, 4, Idea.Status.class, "ideas", i));
            idea.setPromotedProject(nullable(row, 6).isBlank() ? null : require(projects, text(row, 6), "Неизвестный promotedProjectExternalId: " + text(row, 6)));
            idea = ideas.save(idea); bind(user, "idea", idea.getId(), xid); result.put(xid, idea); count(isNew, "ideas", created, updated);
        }
        return result;
    }

    private void applyRoutineSchedules(User user, Workbook workbook, Map<String, Routine> routines, Map<String, Integer> created, Map<String, Integer> updated) {
        for (int i = 1; i <= lastRow(workbook, "routine_schedules"); i++) {
            Row row = row(workbook, "routine_schedules", i); String xid = text(row, 0); Routine routine = require(routines, text(row, 1), "Неизвестная Routine: " + text(row, 1));
            RoutineSchedule schedule = existing(user, "routine_schedule", xid, id -> routineSchedules.findById(id).filter(x -> x.getRoutine().getUser().getId().equals(user.getId())).orElseThrow());
            boolean isNew = schedule == null; if (isNew) schedule = RoutineSchedule.builder().routine(routine).build();
            schedule.setRoutine(routine); schedule.setDayOfWeek(enumValue(row, 2, DayOfWeek.class, "routine_schedules", i)); schedule.setStartTime(requiredTime(row, 3, "routine_schedules", i)); schedule.setEndTime(requiredTime(row, 4, "routine_schedules", i));
            schedule = routineSchedules.save(schedule); bind(user, "routine_schedule", schedule.getId(), xid); count(isNew, "routine_schedules", created, updated);
        }
    }

    private void applyTimeEntries(User user, Workbook workbook, Map<String, Delo> delos, Map<String, Integer> created, Map<String, Integer> updated) {
        for (int i = 1; i <= lastRow(workbook, "time_entries"); i++) {
            Row row = row(workbook, "time_entries", i); String xid = text(row, 0);
            TimeEntry entry = existing(user, "time_entry", xid, id -> timeEntries.findById(id).filter(x -> x.getUser().getId().equals(user.getId())).orElseThrow());
            boolean isNew = entry == null; if (isNew) entry = TimeEntry.builder().user(user).build();
            entry.setDelo(nullable(row, 1).isBlank() ? null : require(delos, text(row, 1), "Неизвестный deloExternalId: " + text(row, 1)));
            entry.setAdHocText(nullable(row, 2)); entry.setStartAt(requiredDateTime(row, 3, "time_entries", i)); entry.setEndAt(requiredDateTime(row, 4, "time_entries", i)); entry.setStatus(enumValue(row, 5, TimeEntry.Status.class, "time_entries", i));
            if (entry.getDelo() == null && (entry.getAdHocText() == null || entry.getAdHocText().isBlank()) && entry.getStatus() != TimeEntry.Status.UNKNOWN) throw rowError("time_entries", i, "deloExternalId или adHocText обязателен для статуса отличного от UNKNOWN");
            if (entry.getDelo() != null) entry.setAdHocText(null);
            if (!entry.getStartAt().isBefore(entry.getEndAt())) throw rowError("time_entries", i, "endAt должен быть позже startAt");
            Long existingEntryId = entry.getId();
            List<TimeEntry> overlapping = timeEntries.findOverlapping(user.getId(), entry.getStartAt(), entry.getEndAt());
            if (overlapping.stream().anyMatch(other -> existingEntryId == null || !other.getId().equals(existingEntryId))) throw rowError("time_entries", i, "Интервал пересекается с существующей записью");
            entry = timeEntries.save(entry); bind(user, "time_entry", entry.getId(), xid); count(isNew, "time_entries", created, updated);
        }
    }

    private void applyGoalMetrics(User user, Workbook workbook, Map<String, Goal> goals, Map<String, Integer> created, Map<String, Integer> updated) {
        for (int i = 1; i <= lastRow(workbook, "goal_metrics"); i++) {
            Row row = row(workbook, "goal_metrics", i); String xid = text(row, 0); Goal goal = require(goals, text(row, 1), "Неизвестная Goal: " + text(row, 1));
            GoalMetric metric = existing(user, "goal_metric", xid, id -> goalMetrics.findById(id).filter(x -> x.getGoal().getUser().getId().equals(user.getId())).orElseThrow()); boolean isNew = metric == null; if (isNew) metric = GoalMetric.builder().goal(goal).build();
            metric.setGoal(goal); metric.setKind(required(row, 2, "goal_metrics", i)); metric.setValue(requiredDecimalValue(row, 3, "goal_metrics", i)); metric.setTargetValue(decimal(row, 4)); metric.setAt(requiredDateTime(row, 5, "goal_metrics", i));
            metric = goalMetrics.save(metric); bind(user, "goal_metric", metric.getId(), xid); count(isNew, "goal_metrics", created, updated);
        }
    }

    private void applyGoalBudgets(User user, Workbook workbook, Map<String, Goal> goals, Map<String, Integer> created, Map<String, Integer> updated) {
        for (int i = 1; i <= lastRow(workbook, "goal_week_budgets"); i++) {
            Row row = row(workbook, "goal_week_budgets", i); String xid = text(row, 0); Goal goal = require(goals, text(row, 1), "Неизвестная Goal: " + text(row, 1));
            GoalWeekBudget budget = existing(user, "goal_week_budget", xid, id -> goalWeekBudgets.findById(id).filter(x -> x.getGoal().getUser().getId().equals(user.getId())).orElseThrow()); boolean isNew = budget == null; if (isNew) budget = GoalWeekBudget.builder().goal(goal).build();
            budget.setGoal(goal); budget.setIsoYear(integerRequired(row, 2, "goal_week_budgets", i)); budget.setIsoWeek(integerRequired(row, 3, "goal_week_budgets", i)); budget.setHours(requiredDecimalValue(row, 4, "goal_week_budgets", i));
            budget = goalWeekBudgets.save(budget); bind(user, "goal_week_budget", budget.getId(), xid); count(isNew, "goal_week_budgets", created, updated);
        }
    }


    private void applyNotes(User user, Workbook workbook, Map<String, Project> projects, Map<String, Delo> delos, Map<String, Integer> created, Map<String, Integer> updated) {
        for (int i = 1; i <= lastRow(workbook, "notes"); i++) {
            Row row = row(workbook, "notes", i); String xid = text(row, 0); Project project = nullable(row, 1).isBlank() ? null : require(projects, text(row, 1), "Неизвестный projectExternalId: " + text(row, 1)); Delo delo = nullable(row, 2).isBlank() ? null : require(delos, text(row, 2), "Неизвестный deloExternalId: " + text(row, 2));
            if ((project == null) == (delo == null)) throw rowError("notes", i, "Ровно одна parent-ссылка обязательна");
            Note note = existing(user, "note", xid, id -> notes.findByUserAndId(user, id).orElseThrow()); boolean isNew = note == null; if (isNew) note = Note.builder().user(user).build();
            note.setProject(project); note.setDelo(delo); note.setAuthor(enumValue(row, 3, Note.Author.class, "notes", i)); note.setBody(required(row, 4, "notes", i)); note.setTags(pipe(row, 5).toArray(String[]::new));
            note = notes.save(note); bind(user, "note", note.getId(), xid); count(isNew, "notes", created, updated);
            String audioRef = nullable(row, 6);
            note.setAudioAttachment(audioRef.isBlank() ? null : NoteAttachment.builder().note(note).audioRef(audioRef).contentType(nullable(row, 7)).originalFilename(nullable(row, 8)).build());
        }
    }

    private void applySynergies(User user, Workbook workbook, Map<String, LifeSphere> spheres, Map<String, Project> projects, Map<String, Routine> routines, Map<String, Idea> ideas, Map<String, Integer> created, Map<String, Integer> updated) {
        for (int i = 1; i <= lastRow(workbook, "synergies"); i++) {
            Row row = row(workbook, "synergies", i); String xid = text(row, 0); LifeSphere sphere = require(spheres, text(row, 4), "Неизвестная sphereExternalId: " + text(row, 4));
            Synergy synergy = existing(user, "synergy", xid, id -> synergies.findById(id).filter(x -> x.getUser().getId().equals(user.getId())).orElseThrow()); boolean isNew = synergy == null; if (isNew) synergy = Synergy.builder().user(user).build();
            int targetCount = (!nullable(row, 1).isBlank() ? 1 : 0) + (!nullable(row, 2).isBlank() ? 1 : 0) + (!nullable(row, 3).isBlank() ? 1 : 0);
            if (targetCount != 1) throw rowError("synergies", i, "Ровно одна target-ссылка обязательна");
            synergy.setSphere(sphere); synergy.setProject(nullable(row, 1).isBlank() ? null : require(projects, text(row, 1), "Неизвестный projectExternalId: " + text(row, 1))); synergy.setIdeaId(nullable(row, 2).isBlank() ? null : require(ideas, text(row, 2), "Неизвестный ideaExternalId: " + text(row, 2)).getId()); synergy.setRoutine(nullable(row, 3).isBlank() ? null : require(routines, text(row, 3), "Неизвестный routineExternalId: " + text(row, 3))); synergy.setImpact(enumValue(row, 5, Synergy.Impact.class, "synergies", i));
            synergy = synergies.save(synergy); bind(user, "synergy", synergy.getId(), xid); count(isNew, "synergies", created, updated);
        }
    }

    private void applyProjectDependencies(User user, Workbook workbook, Map<String, Project> projects, Map<String, Integer> created, Map<String, Integer> updated) {
        for (int i = 1; i <= lastRow(workbook, "project_dependencies"); i++) {
            Row row = row(workbook, "project_dependencies", i); String xid = text(row, 0); Project blocker = require(projects, text(row, 1), "Неизвестный blockerExternalId: " + text(row, 1)); Project blocked = require(projects, text(row, 2), "Неизвестный blockedExternalId: " + text(row, 2));
            if (blocker.getId().equals(blocked.getId())) throw rowError("project_dependencies", i, "Проект не может зависеть от самого себя");
            ProjectDependency dependency = projectDependencies.findById(new ProjectDependencyId(blocker.getId(), blocked.getId())).filter(x -> x.getUser().getId().equals(user.getId())).orElse(null); boolean isNew = dependency == null;
            if (isNew) dependency = ProjectDependency.builder().blocker(blocker).blocked(blocked).user(user).build();
            projectDependencies.save(dependency);
            count(isNew, "project_dependencies", created, updated);
        }
    }

    private void applyBacklog(User user, Workbook workbook, Map<String, Delo> delos, Map<String, Integer> created, Map<String, Integer> updated) {
        for (int i = 1; i <= lastRow(workbook, "backlog_items"); i++) {
            Row row = row(workbook, "backlog_items", i); String xid = text(row, 0); Delo delo = require(delos, text(row, 1), "Неизвестный deloExternalId: " + text(row, 1));
            BacklogItem item = existing(user, "backlog_item", xid, id -> backlogItems.findById(id).filter(x -> x.getUser().getId().equals(user.getId())).orElseThrow()); boolean isNew = item == null; if (isNew) item = BacklogItem.builder().user(user).delo(delo).build();
            item.setDelo(delo); item.setScope(enumValue(row, 2, BacklogItem.Scope.class, "backlog_items", i)); item.setPeriodId(required(row, 3, "backlog_items", i)); item.setPlannedHours(decimal(row, 4)); item.setPosition(integer(row, 5, 0)); item.setMovedToWeek(nullable(row, 6));
            item = backlogItems.save(item); bind(user, "backlog_item", item.getId(), xid); count(isNew, "backlog_items", created, updated);
        }
    }

    private void applyChecklist(User user, Workbook workbook, Map<String, Delo> delos, Map<String, Integer> created, Map<String, Integer> updated) {
        for (int i = 1; i <= lastRow(workbook, "checklist_items"); i++) {
            Row row = row(workbook, "checklist_items", i); String xid = text(row, 0); DailyChecklistItem item = existing(user, "checklist_item", xid, id -> checklistItems.findById(id).filter(x -> x.getUser().getId().equals(user.getId())).orElseThrow()); boolean isNew = item == null; if (isNew) item = DailyChecklistItem.builder().user(user).build();
            item.setDate(requiredDate(row, 1, "checklist_items", i)); item.setTitle(required(row, 2, "checklist_items", i)); item.setDelo(nullable(row, 3).isBlank() ? null : require(delos, text(row, 3), "Неизвестный deloExternalId: " + text(row, 3))); item.setPosition(integer(row, 4, 0)); item.setDone(bool(row, 5, false)); item.setDoneAt(nullable(row, 6).isBlank() ? null : Instant.parse(text(row, 6)));
            item = checklistItems.save(item); bind(user, "checklist_item", item.getId(), xid); count(isNew, "checklist_items", created, updated);
        }
    }

    private void applyActivityMappings(User user, Workbook workbook, Map<String, Delo> delos, Map<String, Integer> created, Map<String, Integer> updated) {
        for (int i = 1; i <= lastRow(workbook, "activity_mappings"); i++) {
            Row row = row(workbook, "activity_mappings", i); String xid = text(row, 0); Delo delo = require(delos, text(row, 2), "Неизвестный deloExternalId: " + text(row, 2)); ActivityMapping mapping = existing(user, "activity_mapping", xid, id -> activityMappings.findById(id).filter(x -> x.getUser().getId().equals(user.getId())).orElseThrow()); boolean isNew = mapping == null; if (isNew) mapping = ActivityMapping.builder().user(user).build();
            mapping.setActivityText(required(row, 1, "activity_mappings", i)); mapping.setDelo(delo); mapping = activityMappings.save(mapping); bind(user, "activity_mapping", mapping.getId(), xid); count(isNew, "activity_mappings", created, updated);
        }
    }

    private void deleteMissing(User user, Workbook workbook, List<String> scopes, Map<String, Integer> deleted) {
        Set<String> allowed = Set.of("life_areas", "life_spheres", "projects", "routines", "routine_schedules", "delos", "time_entries", "goals", "goal_metrics", "goal_week_budgets", "ideas", "notes", "synergies", "project_dependencies", "backlog_items", "checklist_items", "activity_mappings");
        for (String scope : scopes) {
            if (!allowed.contains(scope)) throw new IllegalArgumentException("Неразрешенный delete scope: " + scope);
            String sheet = scope;
            Set<String> incoming = new HashSet<>();
            for (int i = 1; i <= lastRow(workbook, sheet); i++) incoming.add(text(row(workbook, sheet, i), 0));
            String entityType = entityType(scope);
            List<SyncExternalId> identities = "project_dependencies".equals(scope)
                    ? externalIds.findByUserAndEntityTypeStartingWith(user, "project_dependency:")
                    : externalIds.findByUserAndEntityType(user, entityType);
            Set<String> incomingKeys = new HashSet<>();
            if ("project_dependencies".equals(scope)) for (int i = 1; i <= lastRow(workbook, sheet); i++) incomingKeys.add("project_dependency:" + text(row(workbook, sheet, i), 1) + ":" + text(row(workbook, sheet, i), 2));
            for (SyncExternalId identity : identities) {
                if (!("project_dependencies".equals(scope) ? incomingKeys.contains(identity.getEntityType()) : incoming.contains(identity.getExternalId()))) {
                    deleteEntity(scope, identity.getEntityId(), user);
                    externalIds.delete(identity);
                    deleted.merge(scope, 1, Integer::sum);
                }
            }
        }
    }

    private void deleteEntity(String scope, Long id, User user) {
        switch (scope) {
            case "life_areas" -> lifeAreas.findByUserAndId(user, id).ifPresent(lifeAreas::delete);
            case "life_spheres" -> lifeSpheres.findByUserAndId(user, id).ifPresent(lifeSpheres::delete);
            case "projects" -> projects.findByUserAndId(user, id).ifPresent(projects::delete);
            case "routines" -> routines.findByUserAndId(user, id).ifPresent(routines::delete);
            case "routine_schedules" -> routineSchedules.findById(id).filter(x -> x.getRoutine().getUser().getId().equals(user.getId())).ifPresent(routineSchedules::delete);
            case "delos" -> delos.findByUserAndId(user, id).ifPresent(delos::delete);
            case "goals" -> goals.findByUserAndId(user, id).ifPresent(goals::delete);
            case "goal_metrics" -> goalMetrics.findById(id).filter(x -> x.getGoal().getUser().getId().equals(user.getId())).ifPresent(goalMetrics::delete);
            case "goal_week_budgets" -> goalWeekBudgets.findById(id).filter(x -> x.getGoal().getUser().getId().equals(user.getId())).ifPresent(goalWeekBudgets::delete);
            case "ideas" -> ideas.findByUserAndId(user, id).ifPresent(ideas::delete);
            case "notes" -> notes.findByUserAndId(user, id).ifPresent(notes::delete);
            case "synergies" -> synergies.findById(id).filter(x -> x.getUser().getId().equals(user.getId())).ifPresent(synergies::delete);
            case "time_entries" -> timeEntries.findById(id).filter(x -> x.getUser().getId().equals(user.getId())).ifPresent(timeEntries::delete);
            case "backlog_items" -> backlogItems.findById(id).filter(x -> x.getUser().getId().equals(user.getId())).ifPresent(backlogItems::delete);
            case "checklist_items" -> checklistItems.findById(id).filter(x -> x.getUser().getId().equals(user.getId())).ifPresent(checklistItems::delete);
            case "activity_mappings" -> activityMappings.findById(id).filter(x -> x.getUser().getId().equals(user.getId())).ifPresent(activityMappings::delete);
            case "project_dependencies" -> {
                projectDependencies.findAllForUser(user).stream()
                        .filter(x -> x.getBlocker().getId().equals(id) || x.getBlocked().getId().equals(id))
                        .forEach(projectDependencies::delete);
            }
            default -> throw new IllegalArgumentException("Неразрешенный delete scope: " + scope);
        }
    }

    private static String entityType(String scope) {
        return switch (scope) {
            case "life_areas" -> "life_area";
            case "life_spheres" -> "life_sphere";
            case "projects" -> "project";
            case "routines" -> "routine";
            case "delos" -> "delo";
            case "goals" -> "goal";
            case "ideas" -> "idea";
            case "notes" -> "note";
            case "time_entries" -> "time_entry";
            case "backlog_items" -> "backlog_item";
            case "checklist_items" -> "checklist_item";
            case "activity_mappings" -> "activity_mapping";
            default -> throw new IllegalArgumentException("Неразрешенный delete scope: " + scope);
        };
    }

    private <T> T existing(User user, String type, String xid, java.util.function.Function<Long, T> loader) {
        if (xid.isBlank()) throw new IllegalArgumentException("externalId обязателен");
        return externalIds.findByUserAndEntityTypeAndExternalId(user, type, xid).map(x -> loader.apply(x.getEntityId())).orElse(null);
    }

    private void bind(User user, String type, Long entityId, String xid) {
        externalIds.findByUserAndEntityTypeAndExternalId(user, type, xid).ifPresentOrElse(x -> x.setEntityId(entityId), () -> externalIds.save(SyncExternalId.builder().user(user).entityType(type).entityId(entityId).externalId(xid).build()));
    }


    private static void count(boolean isNew, String sheet, Map<String, Integer> created, Map<String, Integer> updated) { (isNew ? created : updated).merge(sheet, 1, Integer::sum); }
    private static int lastRow(Workbook workbook, String sheet) { return workbook.getSheet(sheet).getLastRowNum(); }
    private static Row row(Workbook workbook, String sheet, int index) { return workbook.getSheet(sheet).getRow(index); }
    private static String text(Row row, int col) { return row == null || row.getCell(col) == null ? "" : new DataFormatter().formatCellValue(row.getCell(col)).trim(); }
    private static String nullable(Row row, int col) { return text(row, col); }
    private static String required(Row row, int col, String sheet, int rowIndex) { String value = text(row, col); if (value.isBlank()) throw rowError(sheet, rowIndex, "Поле обязательно: " + col); return value; }
    private static String requiredDecimal(Row row, int col, String sheet, int rowIndex) { String value = required(row, col, sheet, rowIndex); try { new BigDecimal(value); return value; } catch (NumberFormatException e) { throw rowError(sheet, rowIndex, "Некорректное число: " + value); } }
    private static BigDecimal decimal(Row row, int col) { String value = text(row, col); return value.isBlank() ? null : new BigDecimal(value); }
    private static BigDecimal requiredDecimalValue(Row row, int col, String sheet, int rowIndex) { return new BigDecimal(requiredDecimal(row, col, sheet, rowIndex)); }
    private static int integer(Row row, int col, int fallback) { String value = text(row, col); return value.isBlank() ? fallback : Integer.parseInt(value); }
    private static int integerRequired(Row row, int col, String sheet, int rowIndex) { String value = required(row, col, sheet, rowIndex); try { return Integer.parseInt(value); } catch (NumberFormatException e) { throw rowError(sheet, rowIndex, "Некорректное целое число: " + value); } }
    private static boolean bool(Row row, int col, boolean fallback) { String value = text(row, col); return value.isBlank() ? fallback : Boolean.parseBoolean(value); }
    private static LocalDate date(Row row, int col) { String value = text(row, col); return value.isBlank() ? null : LocalDate.parse(value); }
    private static LocalDate requiredDate(Row row, int col, String sheet, int rowIndex) { String value = required(row, col, sheet, rowIndex); try { return LocalDate.parse(value); } catch (RuntimeException e) { throw rowError(sheet, rowIndex, "Некорректная дата: " + value); } }
    private static LocalDateTime requiredDateTime(Row row, int col, String sheet, int rowIndex) { String value = required(row, col, sheet, rowIndex); try { return LocalDateTime.parse(value); } catch (RuntimeException e) { throw rowError(sheet, rowIndex, "Некорректная дата-время: " + value); } }
    private static LocalTime time(Row row, int col) { String value = text(row, col); return value.isBlank() ? null : LocalTime.parse(value); }
    private static LocalTime requiredTime(Row row, int col, String sheet, int rowIndex) { String value = required(row, col, sheet, rowIndex); try { return LocalTime.parse(value); } catch (RuntimeException e) { throw rowError(sheet, rowIndex, "Некорректное время: " + value); } }
    private static <E extends Enum<E>> E enumValue(Row row, int col, Class<E> type, String sheet, int rowIndex) { String value = required(row, col, sheet, rowIndex); try { return Enum.valueOf(type, value); } catch (RuntimeException e) { throw rowError(sheet, rowIndex, "Некорректное значение enum: " + value); } }
    private static Set<String> pipe(Row row, int col) { String value = text(row, col); Set<String> result = new HashSet<>(); if (!value.isBlank()) for (String item : value.split("\\|")) if (!item.isBlank()) result.add(item.trim()); return result; }
    private static IllegalArgumentException rowError(String sheet, int row, String message) { return new IllegalArgumentException(sheet + ", строка " + (row + 1) + ": " + message); }
    private static <T> T require(Map<String, T> values, String key, String message) { T value = values.get(key); if (value == null) throw new IllegalArgumentException(message); return value; }

    public record ApplyResponse(Long previewId, Map<String, Integer> created, Map<String, Integer> updated, Map<String, Integer> deleted, String status) { }
}
