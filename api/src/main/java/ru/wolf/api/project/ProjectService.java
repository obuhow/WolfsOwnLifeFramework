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
import ru.wolf.api.aggregate.FactAggregate;
import ru.wolf.api.aggregate.FactAggregateService;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.lifearea.LifeArea;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.project.dto.*;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final LifeAreaRepository lifeAreaRepository;
    private final UserRepository userRepository;
    private final DeloProjectRepository deloProjectRepository;
    private final FactAggregateService factAggregateService;
    private final ResourceCascadeService resourceCascadeService;
    private final PlanDistributionService planDistributionService;

    @Transactional(readOnly = true)
    public List<ProjectResponse> listProjects(String username, Long lifeAreaId) {
        User user = currentUser(username);
        List<Project> projects = lifeAreaId == null
                ? projectRepository.findByUserOrderByTitleAsc(user)
                : projectRepository.findByUserAndLifeAreaIdOrderByTitleAsc(user, lifeAreaId);
        return projects.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProjectDetailResponse getProject(String username, Long id) {
        User user = currentUser(username);
        Project project = projectRepository.findByUserAndId(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Проект не найден"));
        return toDetailResponse(project);
    }

    @Transactional
    public ProjectResponse createProject(String username, CreateProjectRequest request) {
        User user = currentUser(username);
        validateDates(request.startDate(), request.endDate());
        LifeArea lifeArea = lifeAreaRepository.findByUserAndId(user, request.lifeAreaId())
                .orElseThrow(() -> new IllegalArgumentException("Область жизни не найдена"));
        Project parent = resolveParent(user, request.parentId(), lifeArea);
        Project project = Project.builder()
                .user(user)
                .lifeArea(lifeArea)
                .parent(parent)
                .title(request.title().trim())
                .status(request.status() == null ? Project.Status.IN_PROGRESS : request.status())
                .description(normalizeDescription(request.description()))
                .startDate(request.startDate())
                .endDate(request.endDate())
                .totalPlanHours(request.totalPlanHours())
                .planDistribution(request.planDistribution() == null ? Project.PlanDistribution.NONE : request.planDistribution())
                .planFrozenAt(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()))
                .build();
        return toResponse(projectRepository.save(project));
    }

    @Transactional
    public ProjectResponse updateProject(String username, Long id, UpdateProjectRequest request) {
        User user = currentUser(username);
        Project project = projectRepository.findByUserAndId(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Проект не найден"));
        validateDates(request.startDate(), request.endDate());
        LifeArea lifeArea = lifeAreaRepository.findByUserAndId(user, request.lifeAreaId())
                .orElseThrow(() -> new IllegalArgumentException("Область жизни не найдена"));
        Project parent = resolveParent(user, request.parentId(), lifeArea);
        if (parent != null && parent.getId().equals(project.getId())) {
            throw new IllegalArgumentException("Проект не может быть родителем самому себе");
        }
        if (parent != null && wouldCreateCycle(user, project.getId(), parent.getId())) {
            throw new IllegalArgumentException("Нельзя сделать потомка родителем — образуется цикл");
        }
        if (!project.getLifeArea().getId().equals(lifeArea.getId())) {
            reassignLifeAreaRecursive(user, project, lifeArea);
        }
        project.setLifeArea(lifeArea);
        project.setParent(parent);
        project.setTitle(request.title().trim());
        if (request.status() != null) {
            project.setStatus(request.status());
        }
        project.setDescription(normalizeDescription(request.description()));
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        project.setTotalPlanHours(request.totalPlanHours());
        if (request.planDistribution() != null) {
            project.setPlanDistribution(request.planDistribution());
        }
        return toResponse(projectRepository.save(project));
    }

    @Transactional
    public void deleteProject(String username, Long id) {
        User user = currentUser(username);
        Project project = projectRepository.findByUserAndId(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Проект не найден"));
        List<Project> all = projectRepository.findByUserOrderByTitleAsc(user);
        List<Project> subtree = new ArrayList<>();
        collectSubtree(all, project.getId(), subtree);
        for (int i = subtree.size() - 1; i >= 0; i--) {
            projectRepository.delete(subtree.get(i));
        }
    }

    @Transactional(readOnly = true)
    public ResourceCascadeService.Preview planShiftPreview(String username, Long id, PlanShiftPreviewRequest request) {
        return resourceCascadeService.preview(currentUser(username), id, request.newEnd());
    }

    @Transactional
    public PlanDistributionService.DistributionResult applyPlanDistribution(String username, Long id, PlanDistributionRequest request) {
        User user = currentUser(username);
        Project project = projectRepository.findByUserAndId(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Проект не найден"));
        Project.PlanDistribution mode;
        try {
            mode = Project.PlanDistribution.valueOf(request.mode().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Неизвестный режим распределения: " + request.mode());
        }
        project.setPlanDistribution(mode);
        projectRepository.save(project);
        return planDistributionService.apply(user, project, mode);
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username)
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
                project.getStatus(),
                project.getDescription(),
                project.getStartDate(),
                project.getEndDate(),
                project.getTotalPlanHours(),
                project.getPlanDistribution()
        );
    }

    private ProjectDetailResponse toDetailResponse(Project project) {
        var links = deloProjectRepository.findByProjectId(project.getId());
        List<DeloLink> deloLinks = links.stream()
                .map(l -> new DeloLink(
                        l.getDelo().getId(),
                        l.getDelo().getTitle(),
                        Boolean.TRUE.equals(l.getIsPrimary())
                )).toList();
        FactAggregate aggregates = factAggregateService.forProject(project.getUser(), project.getId());
        return new ProjectDetailResponse(
                project.getId(),
                project.getLifeArea().getId(),
                project.getLifeArea().getName(),
                project.getParent() != null ? project.getParent().getId() : null,
                project.getParent() != null ? project.getParent().getTitle() : null,
                project.getTitle(),
                project.getStatus(),
                project.getDescription(),
                project.getStartDate(),
                project.getEndDate(),
                project.getTotalPlanHours(),
                project.getPlanDistribution(),
                deloLinks,
                aggregates
        );
    }
}
