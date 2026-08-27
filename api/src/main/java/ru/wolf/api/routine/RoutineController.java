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
package ru.wolf.api.routine;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.wolf.api.routine.dto.GoalLinkResponse;
import ru.wolf.api.routine.dto.RoutineRequest;
import ru.wolf.api.routine.dto.RoutineResponse;
import ru.wolf.api.routine.dto.ScheduleRequest;
import ru.wolf.api.routine.dto.ScheduleResponse;

import java.util.List;

@RestController
@RequestMapping("/api/v1/routines")
@RequiredArgsConstructor
public class RoutineController {

    private final RoutineService service;

    @GetMapping
    public ResponseEntity<List<RoutineResponse>> list(
            Authentication authentication,
            @RequestParam(defaultValue = "false") boolean includeArchived
    ) {
        return service.list(authentication.getName(), includeArchived);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoutineResponse> get(Authentication authentication, @PathVariable Long id) {
        return service.get(authentication.getName(), id);
    }

    @PostMapping
    public ResponseEntity<RoutineResponse> create(
            Authentication authentication,
            @Valid @RequestBody RoutineRequest request
    ) {
        return service.create(authentication.getName(), request);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoutineResponse> update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody RoutineRequest request
    ) {
        return service.update(authentication.getName(), id, request);
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<RoutineResponse> archive(Authentication authentication, @PathVariable Long id) {
        return service.archive(authentication.getName(), id);
    }

    @PostMapping("/{id}/schedules")
    public ResponseEntity<ScheduleResponse> addSchedule(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody ScheduleRequest request
    ) {
        return service.addSchedule(authentication.getName(), id, request);
    }

    @DeleteMapping("/{id}/schedules/{scheduleId}")
    public ResponseEntity<Void> deleteSchedule(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long scheduleId
    ) {
        return service.deleteSchedule(authentication.getName(), id, scheduleId);
    }

    @PostMapping("/{id}/goals/{goalId}")
    public ResponseEntity<GoalLinkResponse> linkGoal(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long goalId
    ) {
        return service.linkGoal(authentication.getName(), id, goalId);
    }

    @DeleteMapping("/{id}/goals/{goalId}")
    public ResponseEntity<Void> unlinkGoal(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long goalId
    ) {
        return service.unlinkGoal(authentication.getName(), id, goalId);
    }
}
