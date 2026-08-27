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
package ru.wolf.api.goal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.goal.dto.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @GetMapping
    public ResponseEntity<List<GoalResponse>> list(
            Authentication authentication,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return ResponseEntity.ok(goalService.list(authentication.getName(), includeArchived));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GoalDetailResponse> get(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam(required = false) String week) {
        return ResponseEntity.ok(goalService.get(authentication.getName(), id, week));
    }

    @PostMapping
    public ResponseEntity<GoalResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateGoalRequest request) {
        return ResponseEntity.ok(goalService.create(authentication.getName(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GoalResponse> update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateGoalRequest request) {
        return ResponseEntity.ok(goalService.update(authentication.getName(), id, request));
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<Void> archive(Authentication authentication, @PathVariable Long id) {
        goalService.archive(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/priority")
    public ResponseEntity<List<GoalResponse>> changePriority(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody PriorityRequest request) {
        return ResponseEntity.ok(goalService.changePriority(authentication.getName(), id, request));
    }

    @PostMapping("/{id}/budget")
    public ResponseEntity<BudgetResponse> saveBudget(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody BudgetRequest request) {
        return ResponseEntity.ok(goalService.saveBudget(authentication.getName(), id, request));
    }

    @GetMapping("/{id}/budgets")
    public ResponseEntity<List<BudgetResponse>> budgets(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(goalService.budgets(authentication.getName(), id));
    }

    @PostMapping("/{id}/metrics")
    public ResponseEntity<MetricResponse> addMetric(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody MetricRequest request) {
        return ResponseEntity.ok(goalService.addMetric(authentication.getName(), id, request));
    }

    @GetMapping("/{id}/metrics")
    public ResponseEntity<List<MetricResponse>> metrics(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(goalService.metrics(authentication.getName(), id));
    }

    @PatchMapping("/{id}/metrics/{metricId}/increment")
    public ResponseEntity<MetricResponse> incrementMetric(
            Authentication authentication, @PathVariable Long id, @PathVariable Long metricId,
            @Valid @RequestBody IncrementRequest request) {
        return ResponseEntity.ok(goalService.incrementMetric(authentication.getName(), id, metricId, request));
    }

    @GetMapping("/{id}/projects")
    public ResponseEntity<List<ProjectResponse>> projects(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(goalService.projects(authentication.getName(), id));
    }

    @PostMapping("/{id}/projects/{projectId}")
    public ResponseEntity<Void> linkProject(Authentication authentication, @PathVariable Long id, @PathVariable Long projectId) {
        goalService.linkProject(authentication.getName(), id, projectId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/projects/{projectId}")
    public ResponseEntity<Void> unlinkProject(Authentication authentication, @PathVariable Long id, @PathVariable Long projectId) {
        goalService.unlinkProject(authentication.getName(), id, projectId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/fact")
    public ResponseEntity<FactResponse> fact(Authentication authentication, @PathVariable Long id, @RequestParam String week) {
        return ResponseEntity.ok(goalService.fact(authentication.getName(), id, week));
    }
}
