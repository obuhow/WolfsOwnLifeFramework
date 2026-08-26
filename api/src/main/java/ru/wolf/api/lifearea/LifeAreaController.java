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
package ru.wolf.api.lifearea;

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
@RequestMapping("/api/v1/life-areas")
@RequiredArgsConstructor
public class LifeAreaController {

    private final LifeAreaRepository lifeAreaRepository;
    private final ru.wolf.api.user.UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<LifeAreaResponse>> getLifeAreas(Authentication authentication) {
        ru.wolf.api.user.User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        List<LifeArea> areas = lifeAreaRepository.findByUserOrderBySortOrderAscNameAsc(user);
        List<LifeAreaResponse> response = areas.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<LifeAreaResponse> createLifeArea(
            Authentication authentication,
            @Valid @RequestBody CreateLifeAreaRequest request
    ) {
        ru.wolf.api.user.User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        if (lifeAreaRepository.existsByUserAndName(user, request.getName())) {
            throw new IllegalArgumentException("Область жизни с таким именем уже существует");
        }

        int maxSortOrder = lifeAreaRepository.findMaxSortOrderByUser(user);

        LifeArea lifeArea = LifeArea.builder()
                .user(user)
                .name(request.getName())
                .color(request.getColor())
                .sortOrder(maxSortOrder + 1)
                .build();

        LifeArea saved = lifeAreaRepository.save(lifeArea);
        return ResponseEntity.ok(toResponse(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LifeAreaResponse> updateLifeArea(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateLifeAreaRequest request
    ) {
        ru.wolf.api.user.User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        LifeArea lifeArea = lifeAreaRepository.findByUserAndId(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Область жизни не найдена"));

        if (!lifeArea.getName().equals(request.getName()) && lifeAreaRepository.existsByUserAndName(user, request.getName())) {
            throw new IllegalArgumentException("Область жизни с таким именем уже существует");
        }

        lifeArea.setName(request.getName());
        lifeArea.setColor(request.getColor());

        LifeArea saved = lifeAreaRepository.save(lifeArea);
        return ResponseEntity.ok(toResponse(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLifeArea(
            Authentication authentication,
            @PathVariable Long id
    ) {
        ru.wolf.api.user.User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        LifeArea lifeArea = lifeAreaRepository.findByUserAndId(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Область жизни не найдена"));

        lifeAreaRepository.delete(lifeArea);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/move")
    public ResponseEntity<LifeAreaResponse> moveLifeArea(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody MoveLifeAreaRequest request
    ) {
        ru.wolf.api.user.User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        LifeArea lifeArea = lifeAreaRepository.findByUserAndId(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Область жизни не найдена"));

        List<LifeArea> allAreas = lifeAreaRepository.findByUserOrderBySortOrderAscNameAsc(user);
        int oldIndex = -1;
        for (int i = 0; i < allAreas.size(); i++) {
            if (allAreas.get(i).getId().equals(lifeArea.getId())) {
                oldIndex = i;
                break;
            }
        }
        if (oldIndex < 0) {
            throw new IllegalArgumentException("Область жизни не найдена");
        }

        int newIndex = Math.max(0, Math.min(request.getNewIndex(), allAreas.size() - 1));

        if (oldIndex == newIndex) {
            return ResponseEntity.ok(toResponse(lifeArea));
        }

        // Remove from old position and insert at new
        LifeArea moved = allAreas.remove(oldIndex);
        allAreas.add(newIndex, moved);

        // Reassign sort orders
        for (int i = 0; i < allAreas.size(); i++) {
            allAreas.get(i).setSortOrder(i);
        }

        lifeAreaRepository.saveAll(allAreas);
        return ResponseEntity.ok(toResponse(moved));
    }

    private LifeAreaResponse toResponse(LifeArea area) {
        return new LifeAreaResponse(
                area.getId(),
                area.getName(),
                area.getSortOrder(),
                area.getColor()
        );
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LifeAreaResponse {
        private Long id;
        private String name;
        private int sortOrder;
        private String color;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateLifeAreaRequest {
        @NotBlank
        @Size(max = 100)
        private String name;

        @Size(max = 7)
        private String color;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateLifeAreaRequest {
        @NotBlank
        @Size(max = 100)
        private String name;

        @Size(max = 7)
        private String color;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MoveLifeAreaRequest {
        private int newIndex;
    }
}