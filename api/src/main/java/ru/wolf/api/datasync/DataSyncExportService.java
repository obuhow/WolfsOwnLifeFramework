package ru.wolf.api.datasync;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.backlog.BacklogItem;
import ru.wolf.api.checklist.DailyChecklistItem;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloProject;
import ru.wolf.api.goal.Goal;
import ru.wolf.api.goal.GoalMetric;
import ru.wolf.api.goal.GoalProject;
import ru.wolf.api.goal.GoalWeekBudget;
import ru.wolf.api.idea.Idea;
import ru.wolf.api.importxlsx.ActivityMapping;
import ru.wolf.api.lifearea.LifeArea;
import ru.wolf.api.lifesphere.LifeSphere;
import ru.wolf.api.lifesphere.Synergy;
import ru.wolf.api.note.Note;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectDependency;
import ru.wolf.api.routine.Routine;
import ru.wolf.api.routine.RoutineGoal;
import ru.wolf.api.routine.RoutineSchedule;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.user.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DataSyncExportService {
    private final EntityManager entityManager;
    private final SyncExternalIdService externalIds;
    private final DataSyncWorkbookWriter workbookWriter;

    @Transactional
    public byte[] export(User user) throws Exception {
        Map<String, List<Map<String, Object>>> rows = new HashMap<>();
        List<LifeArea> lifeAreas = query("from LifeArea x where x.user = :user order by x.sortOrder, x.name", LifeArea.class, user);
        List<LifeSphere> spheres = query("from LifeSphere x where x.user = :user order by x.sortOrder, x.name", LifeSphere.class, user);
        List<Project> projects = query("from Project x join fetch x.lifeArea left join fetch x.parent where x.user = :user order by x.id", Project.class, user);
        List<Routine> routines = query("from Routine x where x.user = :user order by x.id", Routine.class, user);
        List<Delo> delos = query("select distinct x from Delo x left join fetch x.deloProjects dp left join fetch dp.project where x.user = :user order by x.id", Delo.class, user);
        List<Goal> goals = query("from Goal x where x.user = :user order by x.id", Goal.class, user);
        List<GoalProject> goalProjects = query("from GoalProject x join fetch x.goal join fetch x.project where x.goal.user = :user", GoalProject.class, user);
        List<RoutineGoal> routineGoals = query("from RoutineGoal x join fetch x.routine join fetch x.goal where x.routine.user = :user", RoutineGoal.class, user);
        List<Idea> ideas = query("from Idea x left join fetch x.promotedProject where x.user = :user order by x.id", Idea.class, user);
        List<Note> notes = query("from Note x left join fetch x.project left join fetch x.delo where x.user = :user order by x.id", Note.class, user);
        List<Synergy> synergies = query("from Synergy x join fetch x.sphere left join fetch x.project left join fetch x.routine where x.user = :user order by x.id", Synergy.class, user);
        List<ProjectDependency> dependencies = query("from ProjectDependency x join fetch x.blocker join fetch x.blocked where x.user = :user", ProjectDependency.class, user);
        List<BacklogItem> backlog = query("from BacklogItem x join fetch x.delo where x.user = :user order by x.id", BacklogItem.class, user);
        List<DailyChecklistItem> checklist = query("from DailyChecklistItem x left join fetch x.delo where x.user = :user order by x.id", DailyChecklistItem.class, user);
        List<ActivityMapping> mappings = query("from ActivityMapping x join fetch x.delo where x.user = :user order by x.id", ActivityMapping.class, user);
        List<TimeEntry> timeEntries = query("from TimeEntry x left join fetch x.delo where x.user = :user order by x.startAt", TimeEntry.class, user);

        rows.put("life_areas", lifeAreas.stream().map(x -> map(
                "externalId", xid(user, "life_area", x.getId()), "name", x.getName(), "sortOrder", x.getSortOrder(), "color", x.getColor())).toList());
        rows.put("life_spheres", spheres.stream().map(x -> map(
                "externalId", xid(user, "life_sphere", x.getId()), "name", x.getName(), "sortOrder", x.getSortOrder(), "color", x.getColor(), "archived", x.isArchived())).toList());
        rows.put("projects", projects.stream().map(x -> map(
                "externalId", xid(user, "project", x.getId()), "lifeAreaExternalId", xid(user, "life_area", x.getLifeArea().getId()),
                "parentExternalId", x.getParent() == null ? null : xid(user, "project", x.getParent().getId()), "title", x.getTitle(),
                "status", x.getStatus(), "description", x.getDescription(), "startDate", x.getStartDate(), "endDate", x.getEndDate(),
                "totalPlanHours", x.getTotalPlanHours(), "planDistribution", x.getPlanDistribution(), "planFrozenAt", x.getPlanFrozenAt())).toList());
        Map<Long, List<String>> goalsByRoutine = new HashMap<>();
        routineGoals.forEach(link -> goalsByRoutine.computeIfAbsent(link.getRoutine().getId(), ignored -> new ArrayList<>()).add(xid(user, "goal", link.getGoal().getId())));
        rows.put("routines", routines.stream().map(x -> map(
                "externalId", xid(user, "routine", x.getId()), "title", x.getTitle(), "description", x.getDescription(),
                "weeklyHours", x.getWeeklyHours(), "color", x.getColor(), "icon", x.getIcon(), "archived", x.isArchived(),
                "goalExternalIds", String.join("|", goalsByRoutine.getOrDefault(x.getId(), List.of())))).toList());
        rows.put("routine_schedules", query("from RoutineSchedule x where x.routine.user = :user order by x.id", RoutineSchedule.class, user).stream().map(x -> map(
                "externalId", xid(user, "routine_schedule", x.getId()), "routineExternalId", xid(user, "routine", x.getRoutine().getId()),
                "dayOfWeek", x.getDayOfWeek(), "startTime", x.getStartTime(), "endTime", x.getEndTime())).toList());
        rows.put("delos", delos.stream().map(x -> map(
                "externalId", xid(user, "delo", x.getId()), "title", x.getTitle(), "description", x.getDescription(), "executionMode", x.getExecutionMode(),
                "recurrenceWeekdays", x.getRecurrenceWeekdays(), "recurrenceWindowStart", x.getRecurrenceWindowStart(), "recurrenceWindowEnd", x.getRecurrenceWindowEnd(),
                "recurrenceSlots", x.getRecurrenceSlots(), "projectExternalIds", x.getDeloProjects().stream().map(p -> xid(user, "project", p.getProject().getId())).sorted().reduce((a, b) -> a + "|" + b).orElse(""),
                "primaryProjectExternalId", x.getDeloProjects().stream().filter(DeloProject::getIsPrimary).map(p -> xid(user, "project", p.getProject().getId())).findFirst().orElse(null))).toList());
        rows.put("time_entries", timeEntries.stream().map(x -> map(
                "externalId", xid(user, "time_entry", x.getId()), "deloExternalId", x.getDelo() == null ? null : xid(user, "delo", x.getDelo().getId()),
                "adHocText", x.getAdHocText(), "startAt", x.getStartAt(), "endAt", x.getEndAt(), "status", x.getStatus())).toList());
        Map<Long, List<String>> projectsByGoal = new HashMap<>();
        goalProjects.forEach(link -> projectsByGoal.computeIfAbsent(link.getGoal().getId(), ignored -> new ArrayList<>()).add(xid(user, "project", link.getProject().getId())));
        rows.put("goals", goals.stream().map(x -> map(
                "externalId", xid(user, "goal", x.getId()), "title", x.getTitle(), "description", x.getDescription(), "priority", x.getPriority(), "archived", x.getArchived(),
                "projectExternalIds", String.join("|", projectsByGoal.getOrDefault(x.getId(), List.of())))).toList());
        rows.put("goal_metrics", query("from GoalMetric x where x.goal.user = :user order by x.id", GoalMetric.class, user).stream().map(x -> map(
                "externalId", xid(user, "goal_metric", x.getId()), "goalExternalId", xid(user, "goal", x.getGoal().getId()), "kind", x.getKind(),
                "value", x.getValue(), "targetValue", x.getTargetValue(), "at", x.getAt())).toList());
        rows.put("goal_week_budgets", query("from GoalWeekBudget x where x.goal.user = :user order by x.id", GoalWeekBudget.class, user).stream().map(x -> map(
                "externalId", xid(user, "goal_week_budget", x.getId()), "goalExternalId", xid(user, "goal", x.getGoal().getId()),
                "isoYear", x.getIsoYear(), "isoWeek", x.getIsoWeek(), "hours", x.getHours())).toList());
        rows.put("ideas", ideas.stream().map(x -> map(
                "externalId", xid(user, "idea", x.getId()), "title", x.getTitle(), "description", x.getDescription(), "category", x.getCategory(),
                "status", x.getStatus(), "promotedProjectExternalId", x.getPromotedProject() == null ? null : xid(user, "project", x.getPromotedProject().getId()))).toList());
        rows.put("notes", notes.stream().map(x -> map(
                "externalId", xid(user, "note", x.getId()), "projectExternalId", x.getProject() == null ? null : xid(user, "project", x.getProject().getId()),
                "deloExternalId", x.getDelo() == null ? null : xid(user, "delo", x.getDelo().getId()), "author", x.getAuthor(), "body", x.getBody(),
                "tags", x.getTags() == null ? "" : String.join("|", x.getTags()), "audioRef", x.getAudioAttachment() == null ? null : x.getAudioAttachment().getAudioRef(),
                "audioContentType", x.getAudioAttachment() == null ? null : x.getAudioAttachment().getContentType(), "audioFilename", x.getAudioAttachment() == null ? null : x.getAudioAttachment().getOriginalFilename())).toList());
        rows.put("synergies", synergies.stream().map(x -> map(
                "externalId", xid(user, "synergy", x.getId()), "projectExternalId", x.getProject() == null ? null : xid(user, "project", x.getProject().getId()),
                "ideaExternalId", x.getIdeaId() == null ? null : xid(user, "idea", x.getIdeaId()), "routineExternalId", x.getRoutine() == null ? null : xid(user, "routine", x.getRoutine().getId()),
                "sphereExternalId", xid(user, "life_sphere", x.getSphere().getId()), "impact", x.getImpact())).toList());
        rows.put("project_dependencies", dependencies.stream().map(x -> {
            String blockerExternalId = xid(user, "project", x.getBlocker().getId());
            String blockedExternalId = xid(user, "project", x.getBlocked().getId());
            return map("externalId", dependencyXid(blockerExternalId, blockedExternalId), "blockerExternalId", blockerExternalId, "blockedExternalId", blockedExternalId);
        }).toList());
        rows.put("backlog_items", backlog.stream().map(x -> map(
                "externalId", xid(user, "backlog_item", x.getId()), "deloExternalId", xid(user, "delo", x.getDelo().getId()), "scope", x.getScope(),
                "periodId", x.getPeriodId(), "plannedHours", x.getPlannedHours(), "position", x.getPosition(), "movedToWeek", x.getMovedToWeek())).toList());
        rows.put("checklist_items", checklist.stream().map(x -> map(
                "externalId", xid(user, "checklist_item", x.getId()), "date", x.getDate(), "title", x.getTitle(), "deloExternalId", x.getDelo() == null ? null : xid(user, "delo", x.getDelo().getId()),
                "position", x.getPosition(), "done", x.isDone(), "doneAt", x.getDoneAt())).toList());
        rows.put("activity_mappings", mappings.stream().map(x -> map(
                "externalId", xid(user, "activity_mapping", x.getId()), "activityText", x.getActivityText(), "deloExternalId", xid(user, "delo", x.getDelo().getId()))).toList());
        return workbookWriter.write(DataSyncContract.manifest(), rows);
    }

    private <T> List<T> query(String jpql, Class<T> type, User user) {
        return entityManager.createQuery(jpql, type).setParameter("user", user).getResultList();
    }

    private String xid(User user, String type, Long id) {
        return externalIds.externalId(user, type, id);
    }

    private String dependencyXid(String blockerExternalId, String blockedExternalId) {
        return "project-dependency-" + blockerExternalId + "-" + blockedExternalId;
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new HashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }
}
