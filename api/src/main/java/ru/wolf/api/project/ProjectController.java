package ru.wolf.api.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.lifearea.LifeArea;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final LifeAreaRepository lifeAreaRepository;
    private final UserRepository userRepository;
    private final DeloProjectRepository deloProjectRepository;

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> listProjects(
            Authentication authentication,
            @RequestParam(required = false) Long lifeAreaId
    ) {
        User user = currentUser(authentication);

        List<Project> projects = lifeAreaId == null
                ? projectRepository.findByUserOrderByTitleAsc(user)
                : projectRepository.findByUserAndLifeAreaIdOrderByTitleAsc(user, lifeAreaId);

        List<ProjectResponse> response = projects.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ProjectDetailResponse> getProject(
            Authentication authentication,
            @PathVariable Long id
    ) {
        User user = currentUser(authentication);
        Project project = projectRepository.findByUserAndId(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Проект не найден"));
        return ResponseEntity.ok(toDetailResponse(project));
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            Authentication authentication,
            @Valid @RequestBody CreateProjectRequest request
    ) {
        User user = currentUser(authentication);
        validateDates(request.getStartDate(), request.getEndDate());

        LifeArea lifeArea = lifeAreaRepository.findByUserAndId(user, request.getLifeAreaId())
                .orElseThrow(() -> new IllegalArgumentException("Область жизни не найдена"));

        Project parent = resolveParent(user, request.getParentId(), lifeArea);

        Project project = Project.builder()
                .user(user)
                .lifeArea(lifeArea)
                .parent(parent)
                .title(request.getTitle().trim())
                .description(normalizeDescription(request.getDescription()))
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .totalPlanHours(request.getTotalPlanHours())
                .build();

        Project saved = projectRepository.save(project);
        return ResponseEntity.ok(toResponse(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponse> updateProject(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateProjectRequest request
    ) {
        User user = currentUser(authentication);
        Project project = projectRepository.findByUserAndId(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Проект не найден"));

        validateDates(request.getStartDate(), request.getEndDate());

        LifeArea lifeArea = lifeAreaRepository.findByUserAndId(user, request.getLifeAreaId())
                .orElseThrow(() -> new IllegalArgumentException("Область жизни не найдена"));

        Project parent = resolveParent(user, request.getParentId(), lifeArea);
        if (parent != null && parent.getId().equals(project.getId())) {
            throw new IllegalArgumentException("Проект не может быть родителем самому себе");
        }
        if (parent != null && wouldCreateCycle(user, project.getId(), parent.getId())) {
            throw new IllegalArgumentException("Нельзя сделать потомка родителем — образуется цикл");
        }

        // Moving a subtree to another life area: keep subtree consistent
        if (!project.getLifeArea().getId().equals(lifeArea.getId())) {
            reassignLifeAreaRecursive(user, project, lifeArea);
        }

        project.setLifeArea(lifeArea);
        project.setParent(parent);
        project.setTitle(request.getTitle().trim());
        project.setDescription(normalizeDescription(request.getDescription()));
        project.setStartDate(request.getStartDate());
        project.setEndDate(request.getEndDate());
        project.setTotalPlanHours(request.getTotalPlanHours());

        Project saved = projectRepository.save(project);
        return ResponseEntity.ok(toResponse(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(
            Authentication authentication,
            @PathVariable Long id
    ) {
        User user = currentUser(authentication);
        Project project = projectRepository.findByUserAndId(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Проект не найден"));
        // Self-ref FK: delete children first (DB ON DELETE CASCADE alone is not enough under JPA).
        List<Project> all = projectRepository.findByUserOrderByTitleAsc(user);
        List<Project> subtree = new ArrayList<>();
        collectSubtree(all, project.getId(), subtree);
        // deepest first
        for (int i = subtree.size() - 1; i >= 0; i--) {
            projectRepository.delete(subtree.get(i));
        }
        return ResponseEntity.noContent().build();
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private Project resolveParent(User user, Long parentId, LifeArea lifeArea) {
        if (parentId == null) {
            return null;
        }
        Project parent = projectRepository.findByUserAndId(user, parentId)
                .orElseThrow(() -> new IllegalArgumentException("Родительский проект не найден"));
        if (!parent.getLifeArea().getId().equals(lifeArea.getId())) {
            throw new IllegalArgumentException("Родительский проект должен быть в той же области жизни");
        }
        return parent;
    }

    private boolean wouldCreateCycle(User user, Long projectId, Long newParentId) {
        Set<Long> visited = new HashSet<>();
        Long cursor = newParentId;
        while (cursor != null) {
            if (cursor.equals(projectId)) {
                return true;
            }
            if (!visited.add(cursor)) {
                return true;
            }
            Project current = projectRepository.findByUserAndId(user, cursor).orElse(null);
            if (current == null || current.getParent() == null) {
                return false;
            }
            cursor = current.getParent().getId();
        }
        return false;
    }

    private void reassignLifeAreaRecursive(User user, Project root, LifeArea lifeArea) {
        List<Project> all = projectRepository.findByUserOrderByTitleAsc(user);
        List<Project> subtree = new ArrayList<>();
        collectSubtree(all, root.getId(), subtree);
        for (Project child : subtree) {
            if (!child.getId().equals(root.getId())) {
                child.setLifeArea(lifeArea);
            }
        }
        if (!subtree.isEmpty()) {
            projectRepository.saveAll(subtree);
        }
    }

    private void collectSubtree(List<Project> all, Long rootId, List<Project> acc) {
        for (Project p : all) {
            if (p.getId().equals(rootId)) {
                acc.add(p);
            }
        }
        for (Project p : all) {
            if (p.getParent() != null && p.getParent().getId().equals(rootId)) {
                collectSubtree(all, p.getId(), acc);
            }
        }
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new IllegalArgumentException("Дата окончания не может быть раньше даты начала");
        }
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getLifeArea().getId(),
                project.getParent() != null ? project.getParent().getId() : null,
                project.getTitle(),
                project.getDescription(),
                project.getStartDate(),
                project.getEndDate(),
                project.getTotalPlanHours()
        );
    }

    private ProjectDetailResponse toDetailResponse(Project project) {
        var links = deloProjectRepository.findByProjectId(project.getId());
        List<DeloLink> deloLinks = links.stream()
                .map(l -> new DeloLink(
                        l.getDelo().getId(),
                        l.getDelo().getTitle(),
                        Boolean.TRUE.equals(l.getIsPrimary())
                ))
                .toList();
        return new ProjectDetailResponse(
                project.getId(),
                project.getLifeArea().getId(),
                project.getLifeArea().getName(),
                project.getParent() != null ? project.getParent().getId() : null,
                project.getParent() != null ? project.getParent().getTitle() : null,
                project.getTitle(),
                project.getDescription(),
                project.getStartDate(),
                project.getEndDate(),
                project.getTotalPlanHours(),
                deloLinks,
                null       // aggregates — ticket 13
        );
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectResponse {
        private Long id;
        private Long lifeAreaId;
        private Long parentId;
        private String title;
        private String description;
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal totalPlanHours;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectDetailResponse {
        private Long id;
        private Long lifeAreaId;
        private String lifeAreaName;
        private Long parentId;
        private String parentTitle;
        private String title;
        private String description;
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal totalPlanHours;
        private List<DeloLink> delos;
        private Object aggregates;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeloLink {
        private Long id;
        private String title;
        private Boolean isPrimary;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateProjectRequest {
        @NotNull
        private Long lifeAreaId;

        private Long parentId;

        @NotBlank
        @Size(max = 200)
        private String title;

        @Size(max = 10000)
        private String description;

        private LocalDate startDate;
        private LocalDate endDate;

        @DecimalMin(value = "0.0", inclusive = true)
        private BigDecimal totalPlanHours;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateProjectRequest {
        @NotNull
        private Long lifeAreaId;

        private Long parentId;

        @NotBlank
        @Size(max = 200)
        private String title;

        @Size(max = 10000)
        private String description;

        private LocalDate startDate;
        private LocalDate endDate;

        @DecimalMin(value = "0.0", inclusive = true)
        private BigDecimal totalPlanHours;
    }
}