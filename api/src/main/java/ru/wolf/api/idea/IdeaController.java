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
package ru.wolf.api.idea;

import jakarta.validation.Valid;
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
import ru.wolf.api.lifearea.LifeArea;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.lifesphere.Synergy;
import ru.wolf.api.lifesphere.SynergyRepository;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ideas")
@RequiredArgsConstructor
public class IdeaController {

    private final IdeaRepository ideaRepository;
    private final UserRepository userRepository;
    private final LifeAreaRepository lifeAreaRepository;
    private final ProjectRepository projectRepository;
    private final SynergyRepository synergyRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<IdeaResponse>> list(
            Authentication authentication,
            @RequestParam(required = false) Idea.Category category,
            @RequestParam(required = false) Idea.Status status) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(ideaRepository.findForUser(user, category, status).stream()
                .map(this::toResponse)
                .toList());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<IdeaResponse> get(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(toResponse(findIdea(currentUser(authentication), id)));
    }

    @PostMapping
    public ResponseEntity<IdeaResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateIdeaRequest request) {
        User user = currentUser(authentication);
        Idea idea = Idea.builder()
                .user(user)
                .title(request.getTitle().trim())
                .description(normalize(request.getDescription()))
                .category(request.getCategory())
                .status(request.getStatus() == null ? Idea.Status.BANK : request.getStatus())
                .build();
        return ResponseEntity.ok(toResponse(ideaRepository.save(idea)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<IdeaResponse> update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateIdeaRequest request) {
        Idea idea = findIdea(currentUser(authentication), id);
        idea.setTitle(request.getTitle().trim());
        idea.setDescription(normalize(request.getDescription()));
        idea.setCategory(request.getCategory());
        if (idea.getPromotedProject() != null
                && request.getStatus() != null
                && request.getStatus() != idea.getStatus()) {
            throw new IllegalArgumentException("Статус идеи, взятой в работу, меняется только через её жизненный цикл");
        }
        if (request.getStatus() != null) {
            idea.setStatus(request.getStatus());
        }
        return ResponseEntity.ok(toResponse(ideaRepository.save(idea)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        Idea idea = findIdea(currentUser(authentication), id);
        ideaRepository.delete(idea);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/promote")
    @Transactional
    public ResponseEntity<PromoteResponse> promote(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody PromoteIdeaRequest request) {
        User user = currentUser(authentication);
        Idea idea = ideaRepository.findByUserAndIdForUpdate(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Идея не найдена"));
        if (idea.getPromotedProject() != null || idea.getStatus() == Idea.Status.IN_WORK) {
            throw new IdeaAlreadyPromotedException();
        }
        if (idea.getStatus() != Idea.Status.BANK) {
            throw new IllegalArgumentException("В работу можно взять только идею из банка");
        }

        LifeArea lifeArea = lifeAreaRepository.findByUserAndId(user, request.getLifeAreaId())
                .orElseThrow(() -> new IllegalArgumentException("Область жизни не найдена"));
        Project project = projectRepository.save(Project.builder()
                .user(user)
                .lifeArea(lifeArea)
                .title(idea.getTitle())
                .description(idea.getDescription())
                .build());

        List<Synergy> sourceSynergies = synergyRepository.findByUserAndIdeaIdWithSphere(user, idea.getId());
        for (Synergy source : sourceSynergies) {
            synergyRepository.save(Synergy.builder()
                    .user(user)
                    .project(project)
                    .sphere(source.getSphere())
                    .impact(source.getImpact())
                    .build());
        }

        idea.setPromotedProject(project);
        idea.setStatus(Idea.Status.IN_WORK);
        ideaRepository.save(idea);
        return ResponseEntity.ok(new PromoteResponse(project.getId(), idea.getId()));
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private Idea findIdea(User user, Long id) {
        return ideaRepository.findByUserAndId(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Идея не найдена"));
    }

    private IdeaResponse toResponse(Idea idea) {
        return new IdeaResponse(
                idea.getId(),
                idea.getTitle(),
                idea.getDescription(),
                idea.getCategory(),
                idea.getStatus(),
                idea.getPromotedProject() == null ? null : idea.getPromotedProject().getId(),
                idea.getPromotedProject() == null ? null : idea.getPromotedProject().getTitle()
        );
    }

    private String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateIdeaRequest {
        @NotBlank
        @Size(max = 200)
        private String title;

        @Size(max = 10000)
        private String description;

        @NotNull
        private Idea.Category category;

        private Idea.Status status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateIdeaRequest {
        @NotBlank
        @Size(max = 200)
        private String title;

        @Size(max = 10000)
        private String description;

        @NotNull
        private Idea.Category category;

        private Idea.Status status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromoteIdeaRequest {
        @NotNull
        private Long lifeAreaId;
    }

    @Data
    @AllArgsConstructor
    public static class PromoteResponse {
        private Long projectId;
        private Long ideaId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IdeaResponse {
        private Long id;
        private String title;
        private String description;
        private Idea.Category category;
        private Idea.Status status;
        private Long promotedProjectId;
        private String promotedProjectTitle;
    }
}
