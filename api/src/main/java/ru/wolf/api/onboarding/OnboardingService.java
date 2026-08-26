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
 * along with this program. If not see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.onboarding;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.demo.DemoFixtureGenerator;
import ru.wolf.api.demo.DemoProfile;
import ru.wolf.api.goal.Goal;
import ru.wolf.api.goal.GoalProject;
import ru.wolf.api.goal.GoalProjectId;
import ru.wolf.api.goal.GoalProjectRepository;
import ru.wolf.api.goal.GoalRepository;
import ru.wolf.api.goal.GoalWeekBudget;
import ru.wolf.api.goal.GoalWeekBudgetRepository;
import ru.wolf.api.lifearea.LifeArea;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.onboarding.dto.*;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserPurgeService;
import ru.wolf.api.user.UserRepository;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final UserRepository userRepository;
    private final LifeAreaRepository lifeAreaRepository;
    private final ProjectRepository projectRepository;
    private final GoalRepository goalRepository;
    private final GoalProjectRepository goalProjectRepository;
    private final GoalWeekBudgetRepository goalWeekBudgetRepository;
    private final DemoFixtureGenerator demoFixtureGenerator;
    private final UserPurgeService userPurgeService;
    private final ObjectMapper objectMapper;

    @Transactional
    public Step1Response step1Project(String username, Step1Request request) {
        User user = currentUser(username);
        LifeArea lifeArea = lifeAreaRepository.findFirstByUserOrderBySortOrderAsc(user)
                .orElseGet(() -> createDefaultLifeArea(user));
        Project project = Project.builder()
                .user(user)
                .lifeArea(lifeArea)
                .title(request.title().trim())
                .status(Project.Status.IN_PROGRESS)
                .description("Создано через мастер первого входа")
                .build();
        project = projectRepository.save(project);
        return new Step1Response(project.getId(), project.getTitle());
    }

    @Transactional
    public Step2Response step2Goal(String username, Step2Request request) {
        User user = currentUser(username);
        Project project = projectRepository.findByUserAndId(user, request.projectId())
                .orElseThrow(() -> new IllegalArgumentException("Проект не найден"));
        int priority = goalRepository.findMaxActivePriority(user) + 1;
        Goal goal = Goal.builder()
                .user(user)
                .title(request.title().trim())
                .description("Создано через мастер первого входа")
                .priority(priority)
                .archived(false)
                .build();
        goal = goalRepository.save(goal);
        GoalProject gp = new GoalProject();
        gp.setId(new GoalProjectId(goal.getId(), project.getId()));
        gp.setGoal(goal);
        gp.setProject(project);
        goalProjectRepository.save(gp);
        String currentWeek = getCurrentIsoWeek();
        String[] parts = currentWeek.split("-W");
        int isoYear = Integer.parseInt(parts[0]);
        int isoWeek = Integer.parseInt(parts[1]);
        GoalWeekBudget budget = GoalWeekBudget.builder()
                .goal(goal)
                .isoYear(isoYear)
                .isoWeek(isoWeek)
                .hours(request.weeklyHours().setScale(2, RoundingMode.HALF_UP))
                .build();
        goalWeekBudgetRepository.save(budget);
        return new Step2Response(goal.getId(), goal.getTitle(), budget.getHours());
    }

    @Transactional
    public Step3Response step3WeeklyHours(String username, Step3Request request) {
        User user = currentUser(username);
        user.setAvailableWeeklyHours(request.weeklyHours().setScale(2, RoundingMode.HALF_UP));
        userRepository.save(user);
        return new Step3Response(user.getAvailableWeeklyHours());
    }

    @Transactional
    public CompleteResponse complete(String username) {
        User user = currentUser(username);
        if (user.getOnboardingCompletedAt() == null) {
            user.setOnboardingCompletedAt(Instant.now());
            userRepository.save(user);
        }
        return new CompleteResponse(true, user.getOnboardingCompletedAt());
    }

    @Transactional
    public CompleteResponse skip(String username) {
        User user = currentUser(username);
        if (user.getOnboardingCompletedAt() == null) {
            user.setOnboardingCompletedAt(Instant.now());
            userRepository.save(user);
        }
        return new CompleteResponse(true, user.getOnboardingCompletedAt());
    }

    @Transactional(readOnly = true)
    public StatusResponse status(String username) {
        User user = currentUser(username);
        return new StatusResponse(
                user.getOnboardingCompletedAt() != null,
                user.getOnboardingCompletedAt()
        );
    }

    @Transactional
    public LoadProfileResponse loadProfile(String username, LoadProfileRequest request) {
        User user = currentUser(username);
        return applyProfile(user, request.slug());
    }

    @Transactional
    public LoadProfileResponse reloadProfile(String username, LoadProfileRequest request) {
        User user = currentUser(username);
        String slug = normalizeSlug(request.slug());
        userPurgeService.purgeProfileData(user);
        // Сброс нормы из purgeProfileData здесь не финален: applyProfile ниже
        // выставит норму нового профиля. flush нужен, чтобы удаления легли в БД
        // до проверок идемпотентности внутри populate — иначе они видят старые
        // строки в persistence context и отклоняют загрузку.
        userRepository.saveAndFlush(user);
        return applyProfile(user, slug);
    }

    @Transactional
    public PurgeProfileResponse purgeProfile(String username) {
        User user = currentUser(username);
        userPurgeService.purgeProfileData(user);
        userRepository.save(user);
        return new PurgeProfileResponse(true, user.getAvailableWeeklyHours());
    }

    private LoadProfileResponse applyProfile(User user, String rawSlug) {
        String slug = normalizeSlug(rawSlug);
        String resourcePath = "profiles/" + slug + ".json";
        try (InputStream is = new ClassPathResource(resourcePath).getInputStream()) {
            DemoProfile profile = objectMapper.readValue(is, DemoProfile.class);
            demoFixtureGenerator.populate(user, profile);
            user.setTimezone(profile.timezone() != null ? profile.timezone() : "Europe/Moscow");
            if (profile.weeklyHoursNorm() != null) {
                user.setAvailableWeeklyHours(profile.weeklyHoursNorm());
            }
            userRepository.save(user);
            return new LoadProfileResponse(true, profile.displayName(), profile.weeklyHoursNorm());
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось загрузить профиль: " + slug, e);
        }
    }

    private LifeArea createDefaultLifeArea(User user) {
        LifeArea lifeArea = LifeArea.builder()
                .user(user)
                .name("Мои дела")
                .color("#6366F1")
                .sortOrder(0)
                .build();
        return lifeAreaRepository.save(lifeArea);
    }

    private String getCurrentIsoWeek() {
        LocalDate today = LocalDate.now();
        WeekFields wf = WeekFields.ISO;
        return today.get(wf.weekBasedYear()) + "-W" + String.format("%02d", today.get(wf.weekOfWeekBasedYear()));
    }

    private String normalizeSlug(String rawSlug) {
        String slug = rawSlug.trim().toLowerCase(Locale.ROOT);
        if (!List.of("worker-class", "wise-freelancer", "free-artist").contains(slug)) {
            throw new IllegalArgumentException("Неизвестный профиль: " + slug);
        }
        return slug;
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }
}
