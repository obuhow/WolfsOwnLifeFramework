package ru.wolf.api.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/projects/{id}/dependencies")
@RequiredArgsConstructor
public class ProjectDependencyController {

    private final ProjectRepository projectRepository;
    private final ProjectDependencyRepository dependencyRepository;
    private final UserRepository userRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<DependenciesResponse> list(
            Authentication authentication,
            @PathVariable Long id
    ) {
        User user = currentUser(authentication);
        ensureProject(user, id);

        List<ProjectSummary> blockedBy = dependencyRepository.findBlockedBy(user, id).stream()
                .map(ProjectDependency::getBlocker)
                .map(this::toSummary)
                .toList();
        List<ProjectSummary> blocks = dependencyRepository.findBlocks(user, id).stream()
                .map(ProjectDependency::getBlocked)
                .map(this::toSummary)
                .toList();
        return ResponseEntity.ok(new DependenciesResponse(blockedBy, blocks));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<DependenciesResponse> add(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody AddDependencyRequest request
    ) {
        User user = currentUser(authentication);
        Project blocked = ensureProject(user, id);
        Project blocker = ensureProject(user, request.getBlockerId());

        if (blocker.getId().equals(blocked.getId())) {
            throw new ProjectDependencyCycleException(List.of(blocker.getTitle(), blocked.getTitle()));
        }
        if (dependencyRepository.existsByUserAndBlockerIdAndBlockedId(user, blocker.getId(), blocked.getId())) {
            throw new IllegalArgumentException("Такая зависимость уже существует");
        }

        List<Project> path = findPath(user, blocked.getId(), blocker.getId());
        if (path != null) {
            List<String> cyclePath = new ArrayList<>();
            cyclePath.add(blocker.getTitle());
            path.forEach(project -> cyclePath.add(project.getTitle()));
            throw new ProjectDependencyCycleException(cyclePath);
        }

        dependencyRepository.save(ProjectDependency.builder()
                .blocker(blocker)
                .blocked(blocked)
                .user(user)
                .build());
        return ResponseEntity.ok(dependencies(user, blocked.getId()));
    }

    @DeleteMapping("/{blockerId}")
    @Transactional
    public ResponseEntity<Void> remove(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long blockerId
    ) {
        User user = currentUser(authentication);
        ensureProject(user, id);
        ensureProject(user, blockerId);

        ProjectDependencyId dependencyId = new ProjectDependencyId(blockerId, id);
        if (!dependencyRepository.existsById(dependencyId)) {
            throw new IllegalArgumentException("Такая зависимость не найдена");
        }
        dependencyRepository.deleteById(dependencyId);
        return ResponseEntity.noContent().build();
    }

    private DependenciesResponse dependencies(User user, Long projectId) {
        List<ProjectSummary> blockedBy = dependencyRepository.findBlockedBy(user, projectId).stream()
                .map(ProjectDependency::getBlocker)
                .map(this::toSummary)
                .toList();
        List<ProjectSummary> blocks = dependencyRepository.findBlocks(user, projectId).stream()
                .map(ProjectDependency::getBlocked)
                .map(this::toSummary)
                .toList();
        return new DependenciesResponse(blockedBy, blocks);
    }

    private List<Project> findPath(User user, Long startId, Long targetId) {
        Map<Long, List<Long>> graph = new HashMap<>();
        for (ProjectDependency dependency : dependencyRepository.findAllForUser(user)) {
            graph.computeIfAbsent(dependency.getBlocker().getId(), ignored -> new ArrayList<>())
                    .add(dependency.getBlocked().getId());
        }
        List<Long> pathIds = new ArrayList<>();
        if (!findPathDfs(graph, startId, targetId, new HashSet<>(), pathIds)) {
            return null;
        }
        return pathIds.stream()
                .map(projectId -> projectRepository.findByUserAndId(user, projectId)
                        .orElseThrow(() -> new IllegalStateException("Проект зависимости не найден")))
                .toList();
    }

    private boolean findPathDfs(
            Map<Long, List<Long>> graph,
            Long current,
            Long target,
            Set<Long> visited,
            List<Long> path
    ) {
        if (!visited.add(current)) {
            return false;
        }
        path.add(current);
        if (current.equals(target)) {
            return true;
        }
        for (Long next : graph.getOrDefault(current, List.of())) {
            if (findPathDfs(graph, next, target, visited, path)) {
                return true;
            }
        }
        path.remove(path.size() - 1);
        return false;
    }

    private Project ensureProject(User user, Long projectId) {
        return projectRepository.findByUserAndId(user, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Проект не найден"));
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private ProjectSummary toSummary(Project project) {
        return new ProjectSummary(project.getId(), project.getTitle());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddDependencyRequest {
        @NotNull
        private Long blockerId;
    }

    public record DependenciesResponse(List<ProjectSummary> blockedBy, List<ProjectSummary> blocks) {
    }

    public record ProjectSummary(Long id, String title) {
    }
}
