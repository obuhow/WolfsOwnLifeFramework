package ru.wolf.api.lifesphere;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/synergies")
@RequiredArgsConstructor
public class SynergyController {

    private final SynergyRepository synergyRepository;
    private final LifeSphereRepository lifeSphereRepository;
    private final ru.wolf.api.project.ProjectRepository projectRepository;
    private final ru.wolf.api.user.UserRepository userRepository;

    @PostMapping
    public ResponseEntity<SynergyResponse> createSynergy(
            Authentication authentication,
            @Valid @RequestBody CreateSynergyRequest request
    ) {
        ru.wolf.api.user.User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        LifeSphere sphere = lifeSphereRepository.findByUserAndId(user, request.getSphereId())
                .orElseThrow(() -> new IllegalArgumentException("Сфера жизни не найдена"));

        ru.wolf.api.project.Project project = null;
        if (request.getProjectId() != null) {
            project = projectRepository.findByUserAndId(user, request.getProjectId())
                    .orElseThrow(() -> new IllegalArgumentException("Проект не найден"));
        }

        Long ideaId = request.getIdeaId();

        // Validate: exactly one of projectId or ideaId must be provided
        if ((project == null && ideaId == null) || (project != null && ideaId != null)) {
            throw new IllegalArgumentException("Должен быть указан ровно один из: projectId или ideaId");
        }

        // Check uniqueness
        if (project != null && synergyRepository.existsByUserAndProjectAndSphere(user, project, sphere)) {
            throw new IllegalArgumentException("Связь между этим проектом и сферой уже существует");
        }
        if (ideaId != null && synergyRepository.existsByUserAndIdeaIdAndSphere(user, ideaId, sphere)) {
            throw new IllegalArgumentException("Связь между этой идеей и сферой уже существует");
        }

        Synergy synergy = Synergy.builder()
                .user(user)
                .project(project)
                .ideaId(ideaId)
                .sphere(sphere)
                .impact(request.getImpact())
                .build();

        Synergy saved = synergyRepository.save(synergy);
        return ResponseEntity.ok(toResponse(saved));
    }

    @GetMapping
    public ResponseEntity<List<SynergyResponse>> getSynergies(
            Authentication authentication,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long ideaId
    ) {
        ru.wolf.api.user.User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        List<Synergy> synergies;
        if (projectId != null) {
            ru.wolf.api.project.Project project = projectRepository.findByUserAndId(user, projectId)
                    .orElseThrow(() -> new IllegalArgumentException("Проект не найден"));
            synergies = synergyRepository.findByUserAndProjectWithSphere(user, project);
        } else if (ideaId != null) {
            synergies = synergyRepository.findByUserAndIdeaIdWithSphere(user, ideaId);
        } else {
            // Return all user's synergies
            synergies = synergyRepository.findByUserWithSphere(user);
        }

        List<SynergyResponse> response = synergies.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSynergy(
            Authentication authentication,
            @PathVariable Long id
    ) {
        ru.wolf.api.user.User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        Synergy synergy = synergyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Синергия не найдена"));

        if (!synergy.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Синергия не найдена");
        }

        synergyRepository.delete(synergy);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<SynergyResponse> updateSynergy(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateSynergyRequest request
    ) {
        ru.wolf.api.user.User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        Synergy synergy = synergyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Синергия не найдена"));

        if (!synergy.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Синергия не найдена");
        }

        synergy.setImpact(request.getImpact());
        Synergy saved = synergyRepository.save(synergy);
        
        // Reload with sphere to avoid lazy initialization
        Synergy reloaded = synergyRepository.findById(saved.getId()).orElseThrow();
        return ResponseEntity.ok(toResponse(reloaded));
    }

    private SynergyResponse toResponse(Synergy synergy) {
        return new SynergyResponse(
                synergy.getId(),
                synergy.getProject() != null ? synergy.getProject().getId() : null,
                synergy.getIdeaId(),
                synergy.getSphere().getId(),
                synergy.getSphere().getName(),
                synergy.getSphere().getColor(),
                synergy.getImpact()
        );
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SynergyResponse {
        private Long id;
        private Long projectId;
        private Long ideaId;
        private Long sphereId;
        private String sphereName;
        private String sphereColor;
        private Synergy.Impact impact;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateSynergyRequest {
        @NotNull
        private Long sphereId;

        private Long projectId;
        private Long ideaId;

        @NotNull
        private Synergy.Impact impact;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateSynergyRequest {
        @NotNull
        private Synergy.Impact impact;
    }
}
