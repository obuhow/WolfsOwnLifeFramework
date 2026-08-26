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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.backlog.BacklogItemRepository;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.goal.GoalRepository;
import ru.wolf.api.idea.IdeaRepository;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.lifesphere.LifeSphereRepository;
import ru.wolf.api.lifesphere.SynergyRepository;
import ru.wolf.api.note.Note;
import ru.wolf.api.note.NoteRepository;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.routine.RoutineRepository;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Integration tests for demo profile loading (release 0.6, ticket 01).
 * Verifies POST /api/v1/onboarding/load-profile populates every menu item's
 * data source for each of the three profiles, that dates are relative (always
 * fresh), and that idempotency is preserved.
 */
class DemoProfileLoadIT extends ApiIntegrationTest {

    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ProjectRepository projectRepository;
    @Autowired DeloRepository deloRepository;
    @Autowired TimeEntryRepository timeEntryRepository;
    @Autowired GoalRepository goalRepository;
    @Autowired IdeaRepository ideaRepository;
    @Autowired NoteRepository noteRepository;
    @Autowired SynergyRepository synergyRepository;
    @Autowired RoutineRepository routineRepository;
    @Autowired LifeAreaRepository lifeAreaRepository;
    @Autowired LifeSphereRepository lifeSphereRepository;
    @Autowired BacklogItemRepository backlogItemRepository;
    @Autowired ru.wolf.api.user.UserPurgeService userPurgeService;
    @Autowired org.springframework.transaction.PlatformTransactionManager txManager;

    @BeforeEach
    void cleanup() {
        // Test schema (Hibernate create-drop) has no ON DELETE CASCADE, unlike the
        // Flyway prod schema — purge each non-admin user's data + reference data first.
        // Derived delete queries need an ambient transaction, so wrap the whole teardown.
        new org.springframework.transaction.support.TransactionTemplate(txManager).executeWithoutResult(status -> {
            userRepository.findAll().stream()
                    .filter(u -> !"admin".equals(u.getUsername()))
                    .forEach(u -> {
                        userPurgeService.purgeProfileData(u);
                        lifeAreaRepository.deleteAllByUser(u);
                        lifeSphereRepository.deleteAllByUser(u);
                        userRepository.delete(u);
                    });
        });
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

    private void loadProfile(WebTestClient client, String slug) {
        client.post()
                .uri("/api/v1/onboarding/load-profile")
                .bodyValue(Map.of("slug", slug))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true);
    }

    private void assertAllMenuItemsPopulated(User user) {
        // Projects
        assertThat(projectRepository.findByUserOrderByTitleAsc(user))
                .as("Проекты").isNotEmpty();
        // Delos
        assertThat(deloRepository.findByUserOrderByTitleAsc(user))
                .as("Дела").isNotEmpty();
        // Time entries — 30 days of history
        List<TimeEntry> entries = timeEntryRepository.findOverlapping(
                user.getId(),
                LocalDateTime.now().minusDays(31),
                LocalDateTime.now().plusDays(1));
        assertThat(entries).as("Записи времени").isNotEmpty();
        // Goals
        assertThat(goalRepository.findByUserAndArchivedOrderByPriorityAsc(user, false))
                .as("Цели").isNotEmpty();
        // Ideas
        assertThat(ideaRepository.findForUser(user, null, null))
                .as("Идеи").isNotEmpty();
        // Synergy / competency diagram
        assertThat(synergyRepository.findByUser(user))
                .as("Синергия").isNotEmpty();
        // Routines
        assertThat(routineRepository.findByUserAndArchivedOrderByTitleAsc(user, false))
                .as("Рутины").isNotEmpty();
        // Life spheres seeded (competency diagram base)
        assertThat(lifeSphereRepository.findByUserOrderBySortOrderAscNameAsc(user))
                .as("Сферы жизни").isNotEmpty();
        // Life areas
        assertThat(lifeAreaRepository.findByUserOrderBySortOrderAscNameAsc(user))
                .as("Области жизни").isNotEmpty();
        // Notes — at least one from AGENT
        List<Note> agentNotes = noteRepository.search(user, null, null, "AGENT", null, null);
        assertThat(agentNotes).as("Заметки от Агента").isNotEmpty();
    }

    @Test
    void loadWorkerClassProfile_populatesAllMenuItems() {
        User user = createFreshUser("worker1");
        WebTestClient client = authedClient("worker1", "password123");
        loadProfile(client, "worker-class");
        assertAllMenuItemsPopulated(userRepository.findByUsername("worker1").orElseThrow());
    }

    @Test
    void loadWiseFreelancerProfile_populatesAllMenuItems() {
        createFreshUser("freelancer1");
        WebTestClient client = authedClient("freelancer1", "password123");
        loadProfile(client, "wise-freelancer");
        assertAllMenuItemsPopulated(userRepository.findByUsername("freelancer1").orElseThrow());
    }

    @Test
    void loadFreeArtistProfile_populatesAllMenuItems() {
        createFreshUser("artist1");
        WebTestClient client = authedClient("artist1", "password123");
        loadProfile(client, "free-artist");
        assertAllMenuItemsPopulated(userRepository.findByUsername("artist1").orElseThrow());
    }

    @Test
    void timeEntriesAreRelativeToToday_todayPartiallyFilled() {
        createFreshUser("dated1");
        WebTestClient client = authedClient("dated1", "password123");
        loadProfile(client, "worker-class");
        User user = userRepository.findByUsername("dated1").orElseThrow();

        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Moscow"));

        // History goes back ~30 days and includes an entry dated today (relative, not absolute)
        List<TimeEntry> all = timeEntryRepository.findOverlapping(
                user.getId(),
                today.minusDays(31).atStartOfDay(),
                today.plusDays(1).atStartOfDay());
        assertThat(all).as("30-дневная история").isNotEmpty();

        // The most recent entry is within the last day — data is always fresh
        LocalDateTime maxStart = all.stream()
                .map(TimeEntry::getStartAt)
                .max(java.util.Comparator.naturalOrder())
                .orElseThrow();
        assertThat(maxStart).as("свежесть данных")
                .isAfter(today.minusDays(2).atStartOfDay());

        // Today's non-sleep entries are PLANNED (partial fill of the current day)
        boolean hasTodayPlanned = all.stream()
                .anyMatch(e -> e.getStartAt().toLocalDate().equals(today)
                        && e.getStatus() == TimeEntry.Status.PLANNED);
        assertThat(hasTodayPlanned).as("сегодня частично заполнен планами").isTrue();
    }

    @Test
    void secondLoadOnPopulatedUser_isRejected() {
        createFreshUser("dup1");
        WebTestClient client = authedClient("dup1", "password123");
        loadProfile(client, "worker-class");

        // Second call must fail — idempotency preserved, not silent overwrite
        client.post()
                .uri("/api/v1/onboarding/load-profile")
                .bodyValue(Map.of("slug", "worker-class"))
                .exchange()
                .expectStatus().isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }

    @Test
    void unknownSlug_isRejected() {
        createFreshUser("bad1");
        WebTestClient client = authedClient("bad1", "password123");
        client.post()
                .uri("/api/v1/onboarding/load-profile")
                .bodyValue(Map.of("slug", "does-not-exist"))
                .exchange()
                .expectStatus().isBadRequest();
    }
}