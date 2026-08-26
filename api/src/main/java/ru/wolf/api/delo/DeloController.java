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
package ru.wolf.api.delo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.aggregate.FactAggregate;
import ru.wolf.api.aggregate.FactAggregateService;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.recurrence.RecurrenceService;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/delos")
@RequiredArgsConstructor
public class DeloController {

    private final DeloRepository deloRepository;
    private final DeloProjectRepository deloProjectRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final FactAggregateService factAggregateService;
    private final RecurrenceService recurrenceService;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<DeloResponse>> listDelos(Authentication authentication) {
        User user = currentUser(authentication);
        List<DeloResponse> response = deloRepository.findByUserOrderByTitleAsc(user).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<DeloDetailResponse> getDelo(
            Authentication authentication,
            @PathVariable Long id
    ) {
        User user = currentUser(authentication);
        Delo delo = deloRepository.findByUserAndId(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Дело не найдено"));
        return ResponseEntity.ok(toDetailResponse(delo));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<DeloResponse> createDelo(
            Authentication authentication,
            @Valid @RequestBody CreateDeloRequest request
    ) {
        User user = currentUser(authentication);
        List<Long> projectIds = normalizeIds(request.getProjectIds());
        Long primaryProjectId = request.getPrimaryProjectId();
        validateLinks(user, projectIds, primaryProjectId);

        Delo delo = Delo.builder()
                .user(user)
                .title(request.getTitle().trim())
                .description(normalizeDescription(request.getDescription()))
                .executionMode(request.getExecutionMode() != null ? request.getExecutionMode() : Delo.ExecutionMode.SELF)
                .build();

        Delo saved = deloRepository.save(delo);
        applyProjectLinks(saved, projectIds, primaryProjectId);
        Delo reloaded = deloRepository.findByUserAndId(user, saved.getId()).orElse(saved);
        return ResponseEntity.ok(toResponse(reloaded));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<DeloResponse> updateDelo(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateDeloRequest request
    ) {
        User user = currentUser(authentication);
        Delo delo = deloRepository.findByUserAndId(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Дело не найдено"));

        List<Long> projectIds = normalizeIds(request.getProjectIds());
        Long primaryProjectId = request.getPrimaryProjectId();
        validateLinks(user, projectIds, primaryProjectId);

        delo.setTitle(request.getTitle().trim());
        delo.setDescription(normalizeDescription(request.getDescription()));
        delo.setExecutionMode(request.getExecutionMode() != null ? request.getExecutionMode() : Delo.ExecutionMode.SELF);

        applyProjectLinks(delo, projectIds, primaryProjectId);
        Delo saved = deloRepository.save(delo);
        Delo reloaded = deloRepository.findByUserAndId(user, saved.getId()).orElse(saved);
        return ResponseEntity.ok(toResponse(reloaded));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteDelo(
            Authentication authentication,
            @PathVariable Long id
    ) {
        User user = currentUser(authentication);
        Delo delo = deloRepository.findByUserAndId(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Дело не найдено"));
        deloRepository.delete(delo);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{deloId}/link/{projectId}")
    @Transactional
    public ResponseEntity<DeloResponse> linkProject(
            Authentication authentication,
            @PathVariable Long deloId,
            @PathVariable Long projectId
    ) {
        User user = currentUser(authentication);
        Delo delo = deloRepository.findByUserAndId(user, deloId)
                .orElseThrow(() -> new IllegalArgumentException("Дело не найдено"));
        Project project = projectRepository.findByUserAndId(user, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Проект не найден"));

        boolean alreadyLinked = delo.getDeloProjects().stream()
                .anyMatch(dp -> dp.getProject().getId().equals(projectId));
        if (!alreadyLinked) {
            boolean makePrimary = delo.getDeloProjects().isEmpty();
            DeloProject link = DeloProject.builder()
                    .id(new DeloProjectId(deloId, projectId))
                    .delo(delo)
                    .project(project)
                    .isPrimary(makePrimary)
                    .build();
            delo.getDeloProjects().add(link);
            deloRepository.save(delo);
        }

        Delo reloaded = deloRepository.findByUserAndId(user, deloId).orElseThrow();
        return ResponseEntity.ok(toResponse(reloaded));
    }

    @DeleteMapping("/{deloId}/link/{projectId}")
    @Transactional
    public ResponseEntity<DeloResponse> unlinkProject(
            Authentication authentication,
            @PathVariable Long deloId,
            @PathVariable Long projectId
    ) {
        User user = currentUser(authentication);
        Delo delo = deloRepository.findByUserAndId(user, deloId)
                .orElseThrow(() -> new IllegalArgumentException("Дело не найдено"));

        // Ensure project exists for this user (isolation)
        projectRepository.findByUserAndId(user, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Проект не найден"));

        boolean removed = delo.getDeloProjects().removeIf(dp -> dp.getProject().getId().equals(projectId));
        if (!removed) {
            throw new IllegalArgumentException("Связь Дело–Проект не найдена");
        }

        // If primary removed and links remain — promote first remaining
        boolean hasPrimary = delo.getDeloProjects().stream().anyMatch(dp -> Boolean.TRUE.equals(dp.getIsPrimary()));
        if (!hasPrimary && !delo.getDeloProjects().isEmpty()) {
            delo.getDeloProjects().iterator().next().setIsPrimary(true);
        }

        deloRepository.save(delo);
        Delo reloaded = deloRepository.findByUserAndId(user, deloId).orElseThrow();
        return ResponseEntity.ok(toResponse(reloaded));
    }

    @PutMapping("/{deloId}/primary/{projectId}")
    @Transactional
    public ResponseEntity<DeloResponse> setPrimaryProject(
            Authentication authentication,
            @PathVariable Long deloId,
            @PathVariable Long projectId
    ) {
        User user = currentUser(authentication);
        Delo delo = deloRepository.findByUserAndId(user, deloId)
                .orElseThrow(() -> new IllegalArgumentException("Дело не найдено"));
        projectRepository.findByUserAndId(user, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Проект не найден"));

        DeloProject target = delo.getDeloProjects().stream()
                .filter(dp -> dp.getProject().getId().equals(projectId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Связь Дело–Проект не найдена"));

        for (DeloProject dp : delo.getDeloProjects()) {
            dp.setIsPrimary(dp.getProject().getId().equals(projectId));
        }
        // silence unused
        target.setIsPrimary(true);

        deloRepository.save(delo);
        Delo reloaded = deloRepository.findByUserAndId(user, deloId).orElseThrow();
        return ResponseEntity.ok(toResponse(reloaded));
    }

    @PostMapping("/{id}/apply-recurrence")
    @Transactional
    public ResponseEntity<ApplyRecurrenceResponse> applyRecurrence(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody(required = false) ApplyRecurrenceRequest request
    ) {
        User user = currentUser(authentication);
        ApplyRecurrenceRequest body = request != null ? request : new ApplyRecurrenceRequest();
        RecurrenceService.ApplyResult result = recurrenceService.apply(
                user,
                id,
                new RecurrenceService.ApplyCommand(
                        body.getWeekdays(),
                        body.getWindowStart(),
                        body.getWindowEnd(),
                        body.getHorizonWeeks(),
                        toSlots(body.getSlots())
                )
        );
        return ResponseEntity.ok(new ApplyRecurrenceResponse(
                result.created(),
                result.skippedOccupied(),
                result.skippedPast(),
                result.horizonWeeks(),
                result.from().toString(),
                result.toExclusive().toString()
        ));
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        // preserve order, drop nulls and duplicates
        Set<Long> seen = new HashSet<>();
        List<Long> out = new ArrayList<>();
        for (Long id : ids) {
            if (id != null && seen.add(id)) {
                out.add(id);
            }
        }
        return out;
    }

    private void validateLinks(User user, List<Long> projectIds, Long primaryProjectId) {
        if (projectIds.isEmpty()) {
            if (primaryProjectId != null) {
                throw new IllegalArgumentException("Нельзя задать основной проект без привязанных проектов");
            }
            return;
        }

        List<Project> projects = projectRepository.findByUserAndIdIn(user, projectIds);
        if (projects.size() != projectIds.size()) {
            throw new IllegalArgumentException("Один или несколько проектов не найдены или недоступны");
        }

        if (primaryProjectId != null && !projectIds.contains(primaryProjectId)) {
            throw new IllegalArgumentException("Основной проект должен быть среди привязанных");
        }
    }

    private void applyProjectLinks(Delo delo, List<Long> projectIds, Long primaryProjectId) {
        Set<Long> desired = new HashSet<>(projectIds);

        // remove stale
        delo.getDeloProjects().removeIf(dp -> !desired.contains(dp.getProject().getId()));

        // add missing
        Set<Long> existing = delo.getDeloProjects().stream()
                .map(dp -> dp.getProject().getId())
                .collect(Collectors.toSet());

        for (Long pid : projectIds) {
            if (!existing.contains(pid)) {
                Project project = projectRepository.findByUserAndId(delo.getUser(), pid)
                        .orElseThrow(() -> new IllegalArgumentException("Проект не найден"));
                DeloProject link = DeloProject.builder()
                        .id(new DeloProjectId(delo.getId(), pid))
                        .delo(delo)
                        .project(project)
                        .isPrimary(false)
                        .build();
                delo.getDeloProjects().add(link);
            }
        }

        // primary: explicit or first when linked
        Long effectivePrimary = primaryProjectId;
        if (effectivePrimary == null && !projectIds.isEmpty()) {
            effectivePrimary = projectIds.get(0);
        }
        if (effectivePrimary != null) {
            for (DeloProject dp : delo.getDeloProjects()) {
                dp.setIsPrimary(dp.getProject().getId().equals(effectivePrimary));
            }
        }
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private DeloResponse toResponse(Delo delo) {
        List<Long> projectIds = delo.getDeloProjects().stream()
                .map(l -> l.getProject().getId())
                .sorted()
                .toList();
        Long primaryId = delo.getDeloProjects().stream()
                .filter(l -> Boolean.TRUE.equals(l.getIsPrimary()))
                .map(l -> l.getProject().getId())
                .findFirst()
                .orElse(null);
        return new DeloResponse(
                delo.getId(),
                delo.getTitle(),
                delo.getDescription(),
                delo.getExecutionMode(),
                projectIds,
                primaryId
        );
    }

    private DeloDetailResponse toDetailResponse(Delo delo) {
        List<ProjectLink> projectLinks = delo.getDeloProjects().stream()
                .map(l -> new ProjectLink(l.getProject().getId(), l.getProject().getTitle(), Boolean.TRUE.equals(l.getIsPrimary())))
                .sorted((a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()))
                .toList();
        FactAggregate aggregates = factAggregateService.forDelo(delo.getUser(), delo.getId());
        return new DeloDetailResponse(
                delo.getId(),
                delo.getTitle(),
                delo.getDescription(),
                delo.getExecutionMode(),
                projectLinks,
                delo.getCreatedAt(),
                delo.getUpdatedAt(),
                aggregates,
                RecurrenceService.decodeWeekdays(delo.getRecurrenceWeekdays()),
                delo.getRecurrenceWindowStart(),
                delo.getRecurrenceWindowEnd(),
                toSlotDtos(recurrenceService.slotsOf(delo))
        );
    }

    private List<RecurrenceService.Slot> toSlots(List<RecurrenceSlotDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }
        return dtos.stream()
                .map(d -> new RecurrenceService.Slot(d.getWeekday(), d.getWindowStart(), d.getWindowEnd()))
                .toList();
    }

    private List<RecurrenceSlotDto> toSlotDtos(List<RecurrenceService.Slot> slots) {
        return slots.stream()
                .map(s -> new RecurrenceSlotDto(s.weekday(), s.windowStart(), s.windowEnd()))
                .toList();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeloResponse {
        private Long id;
        private String title;
        private String description;
        private Delo.ExecutionMode executionMode;
        private List<Long> projectIds;
        private Long primaryProjectId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeloDetailResponse {
        private Long id;
        private String title;
        private String description;
        private Delo.ExecutionMode executionMode;
        private List<ProjectLink> projects;
        private Instant createdAt;
        private Instant updatedAt;
        private FactAggregate aggregates;
        private List<DayOfWeek> recurrenceWeekdays;
        private LocalTime recurrenceWindowStart;
        private LocalTime recurrenceWindowEnd;
        private List<RecurrenceSlotDto> recurrenceSlots;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecurrenceSlotDto {
        private DayOfWeek weekday;
        private LocalTime windowStart;
        private LocalTime windowEnd;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplyRecurrenceRequest {
        private List<DayOfWeek> weekdays;
        private LocalTime windowStart;
        private LocalTime windowEnd;
        private Integer horizonWeeks;
        private List<RecurrenceSlotDto> slots;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplyRecurrenceResponse {
        private int created;
        private int skippedOccupied;
        private int skippedPast;
        private int horizonWeeks;
        private String from;
        private String toExclusive;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectLink {
        private Long id;
        private String title;
        private Boolean isPrimary;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateDeloRequest {
        @NotBlank
        @Size(max = 200)
        private String title;

        @Size(max = 10000)
        private String description;

        private Delo.ExecutionMode executionMode;

        private List<Long> projectIds;

        private Long primaryProjectId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateDeloRequest {
        @NotBlank
        @Size(max = 200)
        private String title;

        @Size(max = 10000)
        private String description;

        private Delo.ExecutionMode executionMode;

        private List<Long> projectIds;

        private Long primaryProjectId;
    }
}
