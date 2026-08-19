package ru.wolf.api.lifesphere;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/v1/life-spheres")
@RequiredArgsConstructor
public class LifeSphereController {

    private final LifeSphereRepository lifeSphereRepository;
    private final ru.wolf.api.user.UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<LifeSphereResponse>> getLifeSpheres(Authentication authentication) {
        ru.wolf.api.user.User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        List<LifeSphere> spheres = lifeSphereRepository.findByUserOrderBySortOrderAscNameAsc(user);
        List<LifeSphereResponse> response = spheres.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<LifeSphereResponse> createLifeSphere(
            Authentication authentication,
            @Valid @RequestBody CreateLifeSphereRequest request
    ) {
        ru.wolf.api.user.User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        if (lifeSphereRepository.existsByUserAndName(user, request.getName())) {
            throw new IllegalArgumentException("Сфера жизни с таким именем уже существует");
        }

        int maxSortOrder = lifeSphereRepository.findMaxSortOrderByUser(user);

        LifeSphere lifeSphere = LifeSphere.builder()
                .user(user)
                .name(request.getName())
                .color(request.getColor())
                .sortOrder(maxSortOrder + 1)
                .build();

        LifeSphere saved = lifeSphereRepository.save(lifeSphere);
        return ResponseEntity.ok(toResponse(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LifeSphereResponse> updateLifeSphere(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateLifeSphereRequest request
    ) {
        ru.wolf.api.user.User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        LifeSphere lifeSphere = lifeSphereRepository.findByUserAndId(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Сфера жизни не найдена"));

        if (!lifeSphere.getName().equals(request.getName()) && lifeSphereRepository.existsByUserAndName(user, request.getName())) {
            throw new IllegalArgumentException("Сфера жизни с таким именем уже существует");
        }

        lifeSphere.setName(request.getName());
        lifeSphere.setColor(request.getColor());

        LifeSphere saved = lifeSphereRepository.save(lifeSphere);
        return ResponseEntity.ok(toResponse(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLifeSphere(
            Authentication authentication,
            @PathVariable Long id
    ) {
        ru.wolf.api.user.User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        LifeSphere lifeSphere = lifeSphereRepository.findByUserAndId(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Сфера жизни не найдена"));

        lifeSphereRepository.delete(lifeSphere);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/archive")
    public ResponseEntity<LifeSphereResponse> archiveLifeSphere(
            Authentication authentication,
            @PathVariable Long id
    ) {
        ru.wolf.api.user.User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        LifeSphere lifeSphere = lifeSphereRepository.findByUserAndId(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Сфера жизни не найдена"));

        lifeSphere.setArchived(!lifeSphere.isArchived());
        LifeSphere saved = lifeSphereRepository.save(lifeSphere);
        return ResponseEntity.ok(toResponse(saved));
    }

    @PutMapping("/{id}/move")
    public ResponseEntity<LifeSphereResponse> moveLifeSphere(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody MoveLifeSphereRequest request
    ) {
        ru.wolf.api.user.User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        LifeSphere lifeSphere = lifeSphereRepository.findByUserAndId(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Сфера жизни не найдена"));

        List<LifeSphere> allSpheres = lifeSphereRepository.findByUserOrderBySortOrderAscNameAsc(user);
        int oldIndex = -1;
        for (int i = 0; i < allSpheres.size(); i++) {
            if (allSpheres.get(i).getId().equals(lifeSphere.getId())) {
                oldIndex = i;
                break;
            }
        }
        if (oldIndex < 0) {
            throw new IllegalArgumentException("Сфера жизни не найдена");
        }

        int newIndex = Math.max(0, Math.min(request.getNewIndex(), allSpheres.size() - 1));

        if (oldIndex == newIndex) {
            return ResponseEntity.ok(toResponse(lifeSphere));
        }

        LifeSphere moved = allSpheres.remove(oldIndex);
        allSpheres.add(newIndex, moved);

        for (int i = 0; i < allSpheres.size(); i++) {
            allSpheres.get(i).setSortOrder(i);
        }

        lifeSphereRepository.saveAll(allSpheres);
        return ResponseEntity.ok(toResponse(moved));
    }

    private LifeSphereResponse toResponse(LifeSphere sphere) {
        return new LifeSphereResponse(
                sphere.getId(),
                sphere.getName(),
                sphere.getSortOrder(),
                sphere.getColor(),
                sphere.isArchived()
        );
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LifeSphereResponse {
        private Long id;
        private String name;
        private int sortOrder;
        private String color;
        private boolean archived;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateLifeSphereRequest {
        @NotBlank
        @Size(max = 100)
        private String name;

        @Size(max = 7)
        private String color;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateLifeSphereRequest {
        @NotBlank
        @Size(max = 100)
        private String name;

        @Size(max = 7)
        private String color;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MoveLifeSphereRequest {
        private int newIndex;
    }
}