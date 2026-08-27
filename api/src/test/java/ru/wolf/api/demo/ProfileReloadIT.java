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

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.goal.GoalRepository;
import ru.wolf.api.idea.IdeaRepository;
import ru.wolf.api.lifearea.LifeArea;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.lifesphere.LifeSphereRepository;
import ru.wolf.api.note.NoteRepository;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserPurgeService;
import ru.wolf.api.user.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Повторная загрузка демо-профиля из Настроек (релиз 0.6, тикет 05).
 *
 * <p>Проверяет контракт {@code POST /api/v1/onboarding/reload-profile}: очистка
 * предыдущих данных предшествует наполнению, справочники не дублируются,
 * онбординг не перезапускается.
 */
class ProfileReloadIT extends ApiIntegrationTest {

    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UserPurgeService userPurgeService;
    @Autowired ProjectRepository projectRepository;
    @Autowired DeloRepository deloRepository;
    @Autowired TimeEntryRepository timeEntryRepository;
    @Autowired GoalRepository goalRepository;
    @Autowired IdeaRepository ideaRepository;
    @Autowired NoteRepository noteRepository;
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

    private void load(String username, String slug) {
        authedClient(username, "password123").post()
                .uri("/api/v1/onboarding/load-profile")
                .bodyValue(Map.of("slug", slug))
                .exchange()
                .expectStatus().isOk();
    }

    private void reload(String username, String slug) {
        authedClient(username, "password123").post()
                .uri("/api/v1/onboarding/reload-profile")
                .bodyValue(Map.of("slug", slug))
                .exchange()
                .expectStatus().isOk();
    }

    private long count(String jpql, User user) {
        return tx.execute(status ->
                em.createQuery(jpql, Long.class).setParameter("user", user).getSingleResult());
    }

    /**
     * Testing Decisions, пункт 1: повторная загрузка ТОГО ЖЕ профиля пересоздаёт
     * данные, а не наслаивает их поверх старых.
     */
    @Test
    void reloadSameProfile_recreatesDataInsteadOfDuplicating() {
        createFreshUser("reload-same");
        load("reload-same", "wise-freelancer");

        User user = userRepository.findByUsername("reload-same").orElseThrow();
        List<Project> before = projectRepository.findByUserOrderByTitleAsc(user);
        int projectsBefore = before.size();
        long delosBefore = deloRepository.findByUserOrderByTitleAsc(user).size();
        Set<Long> idsBefore = before.stream().map(Project::getId).collect(Collectors.toSet());
        assertThat(projectsBefore).as("профиль наполнен до перезагрузки").isPositive();

        reload("reload-same", "wise-freelancer");

        User after = userRepository.findByUsername("reload-same").orElseThrow();
        List<Project> afterProjects = projectRepository.findByUserOrderByTitleAsc(after);

        assertThat(afterProjects)
                .as("количество Проектов не удвоилось — данные пересозданы, а не задублированы")
                .hasSize(projectsBefore);
        assertThat(deloRepository.findByUserOrderByTitleAsc(after))
                .as("количество Дел не удвоилось")
                .hasSize((int) delosBefore);

        Set<Long> idsAfter = afterProjects.stream().map(Project::getId).collect(Collectors.toSet());
        assertThat(idsAfter)
                .as("id новые — строки действительно пересозданы, а не переиспользованы")
                .doesNotContainAnyElementsOf(idsBefore);
    }

    /**
     * Testing Decisions, пункт 2: загрузка ДРУГОГО профиля полностью удаляет
     * предыдущий — виден только новый.
     */
    @Test
    void reloadDifferentProfile_removesPreviousDataEntirely() {
        createFreshUser("reload-other");
        load("reload-other", "wise-freelancer");

        User user = userRepository.findByUsername("reload-other").orElseThrow();
        Set<String> freelancerProjects = projectRepository.findByUserOrderByTitleAsc(user).stream()
                .map(Project::getTitle).collect(Collectors.toSet());
        assertThat(freelancerProjects).as("Проекты «Мудрого фрилансера»").isNotEmpty();

        reload("reload-other", "worker-class");

        User after = userRepository.findByUsername("reload-other").orElseThrow();
        Set<String> workerProjects = projectRepository.findByUserOrderByTitleAsc(after).stream()
                .map(Project::getTitle).collect(Collectors.toSet());

        assertThat(workerProjects).as("Проекты «Рабочего класса»").isNotEmpty();
        assertThat(workerProjects)
                .as("ни одного Проекта предыдущего профиля не осталось")
                .doesNotContainAnyElementsOf(freelancerProjects);

        // Норма подтянулась из нового профиля, а не осталась дефолтом после очистки.
        assertThat(after.getAvailableWeeklyHours())
                .as("норма — из нового профиля «Рабочий класс» (15 ч)")
                .isEqualByComparingTo(new BigDecimal("15"));
    }

    /**
     * Регрессия: {@code purgeProfileData} намеренно не удаляет Области жизни
     * (справочник), поэтому наполнение обязано их переиспользовать. Безусловный
     * {@code save} в {@code createLifeArea} дублировал бы их на каждой загрузке.
     */
    @Test
    void reload_doesNotDuplicateLifeAreasOrSpheres() {
        createFreshUser("reload-areas");
        load("reload-areas", "worker-class");

        User user = userRepository.findByUsername("reload-areas").orElseThrow();
        List<String> areaNamesBefore = lifeAreaRepository.findByUserOrderBySortOrderAscNameAsc(user)
                .stream().map(LifeArea::getName).toList();
        assertThat(areaNamesBefore).as("Области жизни после первой загрузки").isNotEmpty();
        assertThat(lifeSphereRepository.findByUserOrderBySortOrderAscNameAsc(user)).hasSize(9);

        reload("reload-areas", "worker-class");
        reload("reload-areas", "worker-class");

        User after = userRepository.findByUsername("reload-areas").orElseThrow();
        List<String> areaNamesAfter = lifeAreaRepository.findByUserOrderBySortOrderAscNameAsc(after)
                .stream().map(LifeArea::getName).toList();

        assertThat(areaNamesAfter)
                .as("после двух перезагрузок Области жизни не размножились")
                .containsExactlyElementsOf(areaNamesBefore);
        assertThat(lifeSphereRepository.findByUserOrderBySortOrderAscNameAsc(after))
                .as("9 Сфер остались девятью").hasSize(9);
    }

    /**
     * Смена профиля не возвращает пользователя в поток первого входа: мастер
     * 0.4-08 запускается только веткой «Очистить профиль» Финального выбора.
     */
    @Test
    void reload_keepsOnboardingCompletedFlag() {
        createFreshUser("reload-flag");
        load("reload-flag", "free-artist");

        authedClient("reload-flag", "password123").post()
                .uri("/api/v1/onboarding/complete")
                .exchange()
                .expectStatus().isOk();

        assertThat(userRepository.findByUsername("reload-flag").orElseThrow().getOnboardingCompletedAt())
                .as("предусловие: онбординг завершён").isNotNull();

        reload("reload-flag", "wise-freelancer");

        assertThat(userRepository.findByUsername("reload-flag").orElseThrow().getOnboardingCompletedAt())
                .as("смена демо-профиля не сбрасывает признак пройденного Знакомства")
                .isNotNull();
    }

    /** Изоляция арендатора: перезагрузка одного профиля не трогает соседний. */
    @Test
    void reload_doesNotTouchOtherUsers() {
        createFreshUser("reload-mine");
        createFreshUser("reload-neighbour");
        load("reload-mine", "wise-freelancer");
        load("reload-neighbour", "worker-class");

        User neighbour = userRepository.findByUsername("reload-neighbour").orElseThrow();
        int neighbourProjects = projectRepository.findByUserOrderByTitleAsc(neighbour).size();
        int neighbourDelos = deloRepository.findByUserOrderByTitleAsc(neighbour).size();

        reload("reload-mine", "free-artist");

        User neighbourAfter = userRepository.findByUsername("reload-neighbour").orElseThrow();
        assertThat(projectRepository.findByUserOrderByTitleAsc(neighbourAfter))
                .as("Проекты соседа целы").hasSize(neighbourProjects);
        assertThat(deloRepository.findByUserOrderByTitleAsc(neighbourAfter))
                .as("Дела соседа целы").hasSize(neighbourDelos);
    }

    /** Осиротевших строк после перезагрузки быть не должно — путь тот же, что и у очистки. */
    @Test
    void reload_leavesNoOrphans() {
        createFreshUser("reload-orphans");
        load("reload-orphans", "wise-freelancer");
        reload("reload-orphans", "worker-class");

        User after = userRepository.findByUsername("reload-orphans").orElseThrow();

        assertThat(count("select count(t) from TimeEntry t where t.user = :user and t.delo is null", after))
                .as("Записи времени без Дела").isZero();
        assertThat(count("select count(dp) from DeloProject dp where dp.delo.user = :user and dp.project.user <> :user", after))
                .as("связки Дело↔Проект чужого пользователя").isZero();

        // Данные нового профиля на месте — очистка не съела наполнение.
        assertThat(projectRepository.findByUserOrderByTitleAsc(after)).as("Проекты нового профиля").isNotEmpty();
        assertThat(timeEntryRepository.findOverlapping(after.getId(),
                LocalDateTime.now().minusDays(400), LocalDateTime.now().plusDays(400)))
                .as("Записи времени нового профиля").isNotEmpty();
    }
}
