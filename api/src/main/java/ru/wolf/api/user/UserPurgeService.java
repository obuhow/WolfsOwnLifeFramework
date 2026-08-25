package ru.wolf.api.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.backlog.BacklogItemRepository;
import ru.wolf.api.checklist.DailyChecklistItemRepository;
import ru.wolf.api.datasync.SyncExternalIdRepository;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.focus.FocusDistractionRepository;
import ru.wolf.api.focus.FocusSessionRepository;
import ru.wolf.api.gantt.WeekPlanRepository;
import ru.wolf.api.goal.GoalMetricRepository;
import ru.wolf.api.goal.GoalProjectRepository;
import ru.wolf.api.goal.GoalRepository;
import ru.wolf.api.goal.GoalWeekBudgetRepository;
import ru.wolf.api.idea.IdeaRepository;
import ru.wolf.api.importxlsx.ActivityMappingRepository;
import ru.wolf.api.lifesphere.SynergyRepository;
import ru.wolf.api.loadcurve.LoadCurveEntryRepository;
import ru.wolf.api.note.NoteRepository;
import ru.wolf.api.project.ProjectDependencyRepository;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.routine.RoutineGoalRepository;
import ru.wolf.api.routine.RoutineRepository;
import ru.wolf.api.routine.RoutineScheduleRepository;
import ru.wolf.api.timeentry.TimeEntryRepository;

import java.math.BigDecimal;

/**
 * Точечная очистка данных профиля с сохранением самого аккаунта и справочников.
 *
 * <p>Используется двумя потоками Знакомства (релиз 0.6): ветка «Очистить профиль»
 * в Финальном выборе (тикет 04) и повторная загрузка профиля из Настроек (тикет 05).
 *
 * <p><b>Не через {@code ON DELETE CASCADE} пользователя.</b> Аккаунт остаётся жить,
 * поэтому каждая таблица чистится явно и в порядке зависимостей: сначала дети,
 * потом родители. Осиротевших строк после вызова быть не должно — это проверяет
 * {@code ProfilePurgeIT}.
 */
@Service
@RequiredArgsConstructor
public class UserPurgeService {

    /** Системный дефолт недельной нормы — совпадает с {@code V21__add_available_weekly_hours.sql}. */
    public static final BigDecimal DEFAULT_WEEKLY_HOURS = BigDecimal.valueOf(30);

    private final ProjectRepository projectRepository;
    private final DeloRepository deloRepository;
    private final DeloProjectRepository deloProjectRepository;
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
    private final RoutineGoalRepository routineGoalRepository;
    private final BacklogItemRepository backlogItemRepository;
    private final WeekPlanRepository weekPlanRepository;
    private final DailyChecklistItemRepository dailyChecklistItemRepository;
    private final FocusSessionRepository focusSessionRepository;
    private final FocusDistractionRepository focusDistractionRepository;
    private final LoadCurveEntryRepository loadCurveEntryRepository;
    private final ActivityMappingRepository activityMappingRepository;
    private final SyncExternalIdRepository syncExternalIdRepository;

    /**
     * Удаляет данные профиля пользователя, сохраняя:
     * <ul>
     *   <li>сам аккаунт;</li>
     *   <li>Области жизни — пользователь настраивал их под себя;</li>
     *   <li>9 Сфер жизни — справочник.</li>
     * </ul>
     * Недельная норма сбрасывается на системный дефолт.
     *
     * <p>Порядок удаления обязателен. {@code focus_session.delo_id} объявлен
     * {@code ON DELETE RESTRICT}: пока сессии фокуса живы, удаление Дел падает
     * на внешнем ключе. Связки без своего {@code user_id}
     * ({@code delo_project}, {@code routine_goal}, {@code load_curve_entry})
     * чистятся через владельца до удаления владельца.
     */
    @Transactional
    public void purgeProfileData(User user) {
        // 1. Листья, ссылающиеся на Дела и Проекты.
        focusDistractionRepository.deleteAllByUser(user);
        focusSessionRepository.deleteAllByUser(user);
        dailyChecklistItemRepository.deleteAllByUser(user);
        activityMappingRepository.deleteAllByUser(user);
        backlogItemRepository.deleteAllByUser(user);
        loadCurveEntryRepository.deleteAllByUser(user);
        weekPlanRepository.deleteAllByUser(user);
        syncExternalIdRepository.deleteAllByUser(user);

        // 2. Ритмы и их связки.
        routineScheduleRepository.deleteByUser(user);
        routineGoalRepository.deleteAllByUser(user);
        synergyRepository.deleteAllByUser(user);
        routineRepository.deleteAllByUser(user);

        // 3. Заметки, Идеи, зависимости Проектов.
        projectDependencyRepository.deleteAllByUser(user);
        noteRepository.deleteAllByUser(user);
        ideaRepository.deleteAllByUser(user);

        // 4. Цели и их дочерние таблицы.
        goalProjectRepository.deleteAllByUser(user);
        goalWeekBudgetRepository.deleteAllByUser(user);
        goalMetricRepository.deleteAllByUser(user);
        goalRepository.deleteAllByUser(user);

        // 5. Записи времени, связки Дело↔Проект, сами Дела и Проекты.
        timeEntryRepository.deleteByUserId(user.getId());
        deloProjectRepository.deleteAllByUser(user);
        deloRepository.deleteAllByUser(user);
        projectRepository.deleteAllByUser(user);

        // 6. Норма — на системный дефолт. Области жизни и Сферы не трогаем.
        user.setAvailableWeeklyHours(DEFAULT_WEEKLY_HOURS);
    }
}
