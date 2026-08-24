package ru.wolf.api.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.backlog.BacklogItemRepository;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.goal.GoalMetricRepository;
import ru.wolf.api.goal.GoalProjectRepository;
import ru.wolf.api.goal.GoalRepository;
import ru.wolf.api.goal.GoalWeekBudgetRepository;
import ru.wolf.api.idea.IdeaRepository;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.lifesphere.LifeSphereRepository;
import ru.wolf.api.lifesphere.SynergyRepository;
import ru.wolf.api.note.NoteRepository;
import ru.wolf.api.project.ProjectDependencyRepository;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.routine.RoutineRepository;
import ru.wolf.api.routine.RoutineScheduleRepository;
import ru.wolf.api.timeentry.TimeEntryRepository;

/**
 * Service for purging user data while preserving the user account and reference data (Life Areas, Life Spheres).
 * Used for demo profile cleanup when switching profiles.
 */
@Service
@RequiredArgsConstructor
public class UserPurgeService {

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
    private final LifeAreaRepository lifeAreaRepository;
    private final LifeSphereRepository lifeSphereRepository;

    /**
     * Deletes all profile data for the given user while preserving:
     * - The user account itself
     * - Life Areas (Области жизни)
     * - Life Spheres (Сферы жизни)
     * Resets weekly hours norm to system default (30h).
     */
    @Transactional
    public void purgeProfileData(User user) {
        // Delete in dependency order: children first
        backlogItemRepository.deleteAllByUser(user);
        routineScheduleRepository.deleteByUser(user);
        routineRepository.deleteAllByUser(user);
        projectDependencyRepository.deleteAllByUser(user);
        noteRepository.deleteAllByUser(user);
        ideaRepository.deleteAllByUser(user);
        synergyRepository.deleteAllByUser(user);
        goalProjectRepository.deleteAllByUser(user);
        goalWeekBudgetRepository.deleteAllByUser(user);
        goalMetricRepository.deleteAllByUser(user);
        goalRepository.deleteAllByUser(user);
        timeEntryRepository.deleteByUserId(user.getId());
        deloRepository.deleteAllByUser(user);
        projectRepository.deleteAllByUser(user);

        // Reset weekly hours norm to default
        user.setAvailableWeeklyHours(java.math.BigDecimal.valueOf(30));
    }
}