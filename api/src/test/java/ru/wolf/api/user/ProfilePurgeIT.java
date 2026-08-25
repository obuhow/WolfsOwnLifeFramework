package ru.wolf.api.user;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.wolf.api.backlog.BacklogItemRepository;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.gantt.WeekPlanRepository;
import ru.wolf.api.goal.GoalRepository;
import ru.wolf.api.idea.IdeaRepository;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.lifesphere.LifeSphereRepository;
import ru.wolf.api.lifesphere.SynergyRepository;
import ru.wolf.api.note.NoteRepository;
import ru.wolf.api.project.ProjectDependencyRepository;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.routine.RoutineRepository;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.timeentry.TimeEntryRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Очистка профиля (релиз 0.6, тикет 04).
 *
 * <p>Ветка «Очистить профиль» Финального выбора удаляет данные пользователя, но не
 * сам аккаунт и не справочники. Профиль сначала наполняется настоящей демо-фикстурой
 * (тикет 01), чтобы проверялась реальная связность данных, а не пустая база.
 */
class ProfilePurgeIT extends ApiIntegrationTest {

    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UserPurgeService userPurgeService;
    @Autowired ProjectRepository projectRepository;
    @Autowired DeloRepository deloRepository;
    @Autowired TimeEntryRepository timeEntryRepository;
    @Autowired GoalRepository goalRepository;
    @Autowired IdeaRepository ideaRepository;
    @Autowired NoteRepository noteRepository;
    @Autowired SynergyRepository synergyRepository;
    @Autowired RoutineRepository routineRepository;
    @Autowired BacklogItemRepository backlogItemRepository;
    @Autowired WeekPlanRepository weekPlanRepository;
    @Autowired ProjectDependencyRepository projectDependencyRepository;
    @Autowired LifeAreaRepository lifeAreaRepository;
    @Autowired LifeSphereRepository lifeSphereRepository;
    @Autowired PlatformTransactionManager txManager;
    @Autowired EntityManager em;

    private TransactionTemplate tx;

    @BeforeEach
    void cleanup() {
        tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(status ->
                userRepository.findAll().stream()
                        .filter(u -> !"admin".equals(u.getUsername()))
                        .forEach(u -> {
                            userPurgeService.purgeProfileData(u);
                            lifeAreaRepository.deleteAllByUser(u);
                            lifeSphereRepository.deleteAllByUser(u);
                            userRepository.delete(u);
                        }));
    }

    private User createFreshUser(String username) {
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode("password123"));
        u.setTimezone("Europe/Moscow");
        u.setHourAccountingMode("PRIMARY_ONLY");
        u.setNightStart(java.time.LocalTime.of(23, 0));
        u.setNightEnd(java.time.LocalTime.of(7, 0));
        u.setDefaultSleepEnd(java.time.LocalTime.of(9, 0));
        return userRepository.save(u);
    }

    /** Наполняет профиль демо-фикстурой «Мудрый фрилансер» — самой богатой из трёх. */
    private User seedLoadedProfile(String username) {
        createFreshUser(username);
        authedClient(username, "password123").post()
                .uri("/api/v1/onboarding/load-profile")
                .bodyValue(Map.of("slug", "wise-freelancer"))
                .exchange()
                .expectStatus().isOk();
        return userRepository.findByUsername(username).orElseThrow();
    }

    private long count(String jpql, User user) {
        return tx.execute(status ->
                em.createQuery(jpql, Long.class).setParameter("user", user).getSingleResult());
    }

    @Test
    void purge_removesProfileData_keepsAccountAndReferenceData() {
        User user = seedLoadedProfile("purge1");
        user.setAvailableWeeklyHours(new BigDecimal("7.50"));
        userRepository.save(user);

        // Предусловие: профиль действительно наполнен.
        assertThat(projectRepository.findByUserOrderByTitleAsc(user)).as("Проекты до очистки").isNotEmpty();
        assertThat(deloRepository.findByUserOrderByTitleAsc(user)).as("Дела до очистки").isNotEmpty();
        assertThat(goalRepository.findByUserAndArchivedOrderByPriorityAsc(user, false))
                .as("Цели до очистки").isNotEmpty();
        int lifeAreasBefore = lifeAreaRepository.findByUserOrderBySortOrderAscNameAsc(user).size();
        int spheresBefore = lifeSphereRepository.findByUserOrderBySortOrderAscNameAsc(user).size();
        assertThat(lifeAreasBefore).as("Области жизни до очистки").isPositive();
        assertThat(spheresBefore).as("9 Сфер до очистки").isEqualTo(9);

        tx.executeWithoutResult(status -> {
            User managed = userRepository.findByUsername("purge1").orElseThrow();
            userPurgeService.purgeProfileData(managed);
            userRepository.save(managed);
        });

        User after = userRepository.findByUsername("purge1").orElseThrow();

        // Аккаунт цел.
        assertThat(after).as("аккаунт сохранён").isNotNull();

        // Данные профиля — ноль.
        assertThat(projectRepository.findByUserOrderByTitleAsc(after)).as("Проекты").isEmpty();
        assertThat(deloRepository.findByUserOrderByTitleAsc(after)).as("Дела").isEmpty();
        assertThat(timeEntryRepository.findOverlapping(after.getId(),
                LocalDateTime.now().minusDays(400), LocalDateTime.now().plusDays(400)))
                .as("Записи времени").isEmpty();
        assertThat(goalRepository.findByUserAndArchivedOrderByPriorityAsc(after, false))
                .as("Цели").isEmpty();
        assertThat(ideaRepository.findForUser(after, null, null)).as("Идеи").isEmpty();
        assertThat(noteRepository.search(after, null, null, null, null, null)).as("Заметки").isEmpty();

        // Справочники целы.
        assertThat(lifeAreaRepository.findByUserOrderBySortOrderAscNameAsc(after))
                .as("Области жизни не тронуты").hasSize(lifeAreasBefore);
        assertThat(lifeSphereRepository.findByUserOrderBySortOrderAscNameAsc(after))
                .as("9 Сфер не тронуты").hasSize(9);

        // Норма — системный дефолт.
        assertThat(after.getAvailableWeeklyHours())
                .as("норма сброшена на дефолт")
                .isEqualByComparingTo(UserPurgeService.DEFAULT_WEEKLY_HOURS);
    }

    @Test
    void purge_leavesNoOrphansInChildTables() {
        User user = seedLoadedProfile("purge2");

        tx.executeWithoutResult(status -> {
            User managed = userRepository.findByUsername("purge2").orElseThrow();
            userPurgeService.purgeProfileData(managed);
            userRepository.save(managed);
        });
        User after = userRepository.findByUsername("purge2").orElseThrow();

        // Синергия, зависимости Проектов, недельные планы — явно перечислены в тикете.
        assertThat(synergyRepository.findByUser(after)).as("Синергия").isEmpty();
        assertThat(count("select count(d) from ProjectDependency d where d.user = :user", after))
                .as("зависимости Проектов").isZero();
        assertThat(count("select count(w) from WeekPlan w where w.user = :user", after))
                .as("недельные планы").isZero();

        // Остальные дочерние таблицы, которые ссылаются на удалённые Дела/Проекты/Цели/Ритмы.
        assertThat(count("select count(b) from BacklogItem b where b.user = :user", after))
                .as("Бэклог").isZero();
        assertThat(count("select count(r) from Routine r where r.user = :user", after))
                .as("Ритмы").isZero();
        assertThat(count("select count(s) from RoutineSchedule s where s.routine.user = :user", after))
                .as("расписания Ритмов").isZero();
        assertThat(count("select count(g) from RoutineGoal g where g.routine.user = :user", after))
                .as("связки Ритм↔Цель").isZero();
        assertThat(count("select count(d) from DeloProject d where d.delo.user = :user", after))
                .as("связки Дело↔Проект").isZero();
        assertThat(count("select count(g) from GoalProject g where g.goal.user = :user", after))
                .as("связки Цель↔Проект").isZero();
        assertThat(count("select count(b) from GoalWeekBudget b where b.goal.user = :user", after))
                .as("недельные бюджеты Целей").isZero();
        assertThat(count("select count(m) from GoalMetric m where m.goal.user = :user", after))
                .as("метрики Целей").isZero();
        assertThat(count("select count(c) from DailyChecklistItem c where c.user = :user", after))
                .as("пункты чек-листа").isZero();
        assertThat(count("select count(f) from FocusSession f where f.user = :user", after))
                .as("сессии фокуса").isZero();
        assertThat(count("select count(m) from ActivityMapping m where m.user = :user", after))
                .as("соответствия активностей").isZero();
        assertThat(count("select count(s) from SyncExternalId s where s.user = :user", after))
                .as("внешние идентификаторы").isZero();
        assertThat(count(
                "select count(e) from LoadCurveEntry e where e.project.user = :user or e.routine.user = :user", after))
                .as("точки кривой нагрузки").isZero();
    }

    @Test
    void purge_isolatesUsers_otherProfileUntouched() {
        User victim = seedLoadedProfile("purge3");
        User bystander = seedLoadedProfile("bystander3");

        int bystanderProjects = projectRepository.findByUserOrderByTitleAsc(bystander).size();
        assertThat(bystanderProjects).isPositive();

        tx.executeWithoutResult(status -> {
            User managed = userRepository.findByUsername("purge3").orElseThrow();
            userPurgeService.purgeProfileData(managed);
            userRepository.save(managed);
        });

        assertThat(projectRepository.findByUserOrderByTitleAsc(
                userRepository.findByUsername("purge3").orElseThrow()))
                .as("Проекты очищенного").isEmpty();
        assertThat(projectRepository.findByUserOrderByTitleAsc(
                userRepository.findByUsername("bystander3").orElseThrow()))
                .as("чужой профиль не тронут").hasSize(bystanderProjects);
        assertThat(victim.getUsername()).isEqualTo("purge3");
    }

    @Test
    void purgeEndpoint_clearsProfile_andKeepsOnboardingIncomplete() {
        createFreshUser("purge4");
        WebTestClient client = authedClient("purge4", "password123");
        client.post().uri("/api/v1/onboarding/load-profile")
                .bodyValue(Map.of("slug", "wise-freelancer"))
                .exchange().expectStatus().isOk();

        client.post().uri("/api/v1/onboarding/purge-profile")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.availableWeeklyHours").isEqualTo(30);

        User after = userRepository.findByUsername("purge4").orElseThrow();
        assertThat(projectRepository.findByUserOrderByTitleAsc(after)).as("Проекты").isEmpty();

        // Флаг онбординга не ставится: гость уходит в мастер первого входа,
        // и завершает онбординг уже там.
        assertThat(after.getOnboardingCompletedAt())
                .as("onboardingCompletedAt остаётся пустым").isNull();

        client.get().uri("/api/v1/onboarding/status")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.onboardingCompleted").isEqualTo(false);
    }

    @Test
    void purgedProfile_canBeRepopulated_secondLoadNoLongerConflicts() {
        createFreshUser("purge5");
        WebTestClient client = authedClient("purge5", "password123");
        client.post().uri("/api/v1/onboarding/load-profile")
                .bodyValue(Map.of("slug", "worker-class"))
                .exchange().expectStatus().isOk();

        // Пока профиль наполнен — повторная загрузка отбивается (тикет 01).
        client.post().uri("/api/v1/onboarding/load-profile")
                .bodyValue(Map.of("slug", "free-artist"))
                .exchange().expectStatus().isEqualTo(org.springframework.http.HttpStatus.CONFLICT);

        client.post().uri("/api/v1/onboarding/purge-profile").exchange().expectStatus().isOk();

        // После очистки — снова можно загрузить (это основа тикета 05).
        client.post().uri("/api/v1/onboarding/load-profile")
                .bodyValue(Map.of("slug", "free-artist"))
                .exchange().expectStatus().isOk();

        User after = userRepository.findByUsername("purge5").orElseThrow();
        List<?> projects = projectRepository.findByUserOrderByTitleAsc(after);
        assertThat(projects).as("профиль наполнен заново").isNotEmpty();
    }
}
