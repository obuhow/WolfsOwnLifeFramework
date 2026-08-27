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
package ru.wolf.api.project;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.project.dto.AddDependencyRequest;
import ru.wolf.api.project.dto.DependenciesResponse;
import ru.wolf.api.project.dto.ProjectSummary;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectDependencyService {

    private final ProjectRepository projectRepository;
    private final ProjectDependencyRepository dependencyRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public DependenciesResponse list(String username, Long id) {
        User user = currentUser(username);
        ensureProject(user, id);
        return dependencies(user, id);
    }

    @Transactional
    public DependenciesResponse add(String username, Long id, AddDependencyRequest request) {
        User user = currentUser(username);
        Project blocked = ensureProject(user, id);
        Project blocker = ensureProject(user, request.blockerId());

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
        return dependencies(user, blocked.getId());
    }

    @Transactional
    public void remove(String username, Long id, Long blockerId) {
        User user = currentUser(username);
        ensureProject(user, id);
        ensureProject(user, blockerId);
        ProjectDependencyId dependencyId = new ProjectDependencyId(blockerId, id);
        if (!dependencyRepository.existsById(dependencyId)) {
            throw new IllegalArgumentException("Такая зависимость не найдена");
        }
        dependencyRepository.deleteById(dependencyId);
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

    private User currentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private ProjectSummary toSummary(Project project) {
        return new ProjectSummary(project.getId(), project.getTitle());
    }
}
