package ru.wolf.api.onboarding;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.demo.DemoFixtureGenerator;
import ru.wolf.api.demo.DemoProfile;
import ru.wolf.api.goal.Goal;
import ru.wolf.api.goal.GoalProject;
import ru.wolf.api.goal.GoalProjectId;
import ru.wolf.api.goal.GoalRepository;
import ru.wolf.api.goal.GoalWeekBudget;
import ru.wolf.api.goal.GoalWeekBudgetRepository;
import ru.wolf.api.goal.GoalProjectRepository;
import ru.wolf.api.lifearea.LifeArea;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Onboarding wizard for new users: Project -> Goal -> Weekly Hours norm.
 * Each step uses existing domain APIs (projects, goals, user settings).
 * Completion sets onboardingCompletedAt timestamp.
 * Also provides profile loading endpoint for demo onboarding (release 0.6).
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final UserRepository userRepository;
    private final LifeAreaRepository lifeAreaRepository;
    private final ProjectRepository projectRepository;
    private final GoalRepository goalRepository;
    private final GoalProjectRepository goalProjectRepository;
    private final GoalWeekBudgetRepository goalWeekBudgetRepository;
    private final DemoFixtureGenerator demoFixtureGenerator;
    private final ObjectMapper objectMapper;

    /**
     * Step 1: Create first project.
     * If user has no Life Areas, silently create "Мои дела" area.
     */
    @PostMapping("/step1/project")
    @Transactional
    public ResponseEntity<Step1Response> step1Project(
            Authentication authentication,
            @Valid @RequestBody Step1Request request
    ) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        // Get or create a default Life Area
        LifeArea lifeArea = lifeAreaRepository.findFirstByUserOrderBySortOrderAsc(user)
                .orElseGet(() -> createDefaultLifeArea(user));

        // Create project
        Project project = Project.builder()
                .user(user)
                .lifeArea(lifeArea)
                .title(request.getTitle().trim())
                .status(Project.Status.IN_PROGRESS)
                .description("Создано через мастер первого входа")
                .build();
        project = projectRepository.save(project);

        return ResponseEntity.ok(new Step1Response(project.getId(), project.getTitle()));
    }

    /**
     * Step 2: Create goal linked to project from step 1.
     */
    @PostMapping("/step2/goal")
    @Transactional
    public ResponseEntity<Step2Response> step2Goal(
            Authentication authentication,
            @Valid @RequestBody Step2Request request
    ) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        // Verify project belongs to user
        Project project = projectRepository.findByUserAndId(user, request.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Проект не найден"));

        int priority = goalRepository.findMaxActivePriority(user) + 1;
        Goal goal = Goal.builder()
                .user(user)
                .title(request.getTitle().trim())
                .description("Создано через мастер первого входа")
                .priority(priority)
                .archived(false)
                .build();
        goal = goalRepository.save(goal);

        // Link goal to project
        GoalProject gp = new GoalProject();
        gp.setId(new ru.wolf.api.goal.GoalProjectId(goal.getId(), project.getId()));
        gp.setGoal(goal);
        gp.setProject(project);
        goalProjectRepository.save(gp);

        // Create weekly budget for current ISO week
        String currentWeek = getCurrentIsoWeek();
        String[] parts = currentWeek.split("-W");
        int isoYear = Integer.parseInt(parts[0]);
        int isoWeek = Integer.parseInt(parts[1]);

        GoalWeekBudget budget = GoalWeekBudget.builder()
                .goal(goal)
                .isoYear(isoYear)
                .isoWeek(isoWeek)
                .hours(request.getWeeklyHours().setScale(2, RoundingMode.HALF_UP))
                .build();
        goalWeekBudgetRepository.save(budget);

        return ResponseEntity.ok(new Step2Response(goal.getId(), goal.getTitle(), budget.getHours()));
    }

    /**
     * Step 3: Set available weekly hours (user setting).
     */
    @PostMapping("/step3/weekly-hours")
    @Transactional
    public ResponseEntity<Step3Response> step3WeeklyHours(
            Authentication authentication,
            @Valid @RequestBody Step3Request request
    ) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        user.setAvailableWeeklyHours(request.getWeeklyHours().setScale(2, RoundingMode.HALF_UP));
        userRepository.save(user);

        return ResponseEntity.ok(new Step3Response(user.getAvailableWeeklyHours()));
    }

    /**
     * Complete onboarding: set onboardingCompletedAt = now().
     * Can be called after any step or skipped entirely.
     */
    @PostMapping("/complete")
    @Transactional
    public ResponseEntity<CompleteResponse> complete(
            Authentication authentication
    ) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        if (user.getOnboardingCompletedAt() == null) {
            user.setOnboardingCompletedAt(Instant.now());
            userRepository.save(user);
        }

        return ResponseEntity.ok(new CompleteResponse(true, user.getOnboardingCompletedAt()));
    }

    /**
     * Skip onboarding: same as complete but without creating entities.
     */
    @PostMapping("/skip")
    @Transactional
    public ResponseEntity<CompleteResponse> skip(
            Authentication authentication
    ) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        if (user.getOnboardingCompletedAt() == null) {
            user.setOnboardingCompletedAt(Instant.now());
            userRepository.save(user);
        }

        return ResponseEntity.ok(new CompleteResponse(true, user.getOnboardingCompletedAt()));
    }

    /**
     * Check onboarding status - used by frontend guard.
     */
    @GetMapping("/status")
    @Transactional(readOnly = true)
    public ResponseEntity<StatusResponse> status(
            Authentication authentication
    ) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        return ResponseEntity.ok(new StatusResponse(
                user.getOnboardingCompletedAt() != null,
                user.getOnboardingCompletedAt()
        ));
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
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.temporal.WeekFields wf = java.time.temporal.WeekFields.ISO;
        return today.get(wf.weekBasedYear()) + "-W" + String.format("%02d", today.get(wf.weekOfWeekBasedYear()));
    }

    /**
     * Load a demo profile by slug (worker-class | wise-freelancer | free-artist).
     * Reads the JSON from assets/profiles/{slug}.json, deserializes to DemoProfile,
     * and calls DemoFixtureGenerator.populate(user, profile).
     * Returns the weeklyHoursNorm from the profile for frontend convenience.
     */
    @PostMapping("/load-profile")
    @Transactional
    public ResponseEntity<LoadProfileResponse> loadProfile(
            Authentication authentication,
            @Valid @RequestBody LoadProfileRequest request
    ) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        String slug = request.getSlug().trim().toLowerCase(java.util.Locale.ROOT);
        if (!List.of("worker-class", "wise-freelancer", "free-artist").contains(slug)) {
            throw new IllegalArgumentException("Неизвестный профиль: " + slug);
        }

        String resourcePath = "profiles/" + slug + ".json";
        try (InputStream is = new ClassPathResource(resourcePath).getInputStream()) {
            DemoProfile profile = objectMapper.readValue(is, DemoProfile.class);
            demoFixtureGenerator.populate(user, profile);
            // Update user's timezone and weekly hours from the profile
            user.setTimezone(profile.timezone() != null ? profile.timezone() : "Europe/Moscow");
            if (profile.weeklyHoursNorm() != null) {
                user.setAvailableWeeklyHours(profile.weeklyHoursNorm());
            }
            userRepository.save(user);
            return ResponseEntity.ok(new LoadProfileResponse(true, profile.displayName(), profile.weeklyHoursNorm()));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Не удалось загрузить профиль: " + slug, e);
        }
    }

    // --- DTOs ---

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Step1Request {
        @NotBlank
        @Size(max = 200)
        private String title;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Step1Response {
        private Long projectId;
        private String projectTitle;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Step2Request {
        @NotNull
        private Long projectId;

        @NotBlank
        @Size(max = 200)
        private String title;

        @NotNull
        @DecimalMin(value = "0.25", inclusive = true)
        private BigDecimal weeklyHours;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Step2Response {
        private Long goalId;
        private String goalTitle;
        private BigDecimal weeklyBudget;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Step3Request {
        @NotNull
        @DecimalMin(value = "1", inclusive = true)
        private BigDecimal weeklyHours;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Step3Response {
        private BigDecimal availableWeeklyHours;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CompleteResponse {
        private boolean success;
        private Instant completedAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class StatusResponse {
        private boolean onboardingCompleted;
        private Instant completedAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class LoadProfileRequest {
        @NotBlank
        @Size(max = 50)
        private String slug;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class LoadProfileResponse {
        private boolean success;
        private String displayName;
        private BigDecimal weeklyHoursNorm;
    }
}