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
package ru.wolf.api.backlog;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.backlog.dto.WeekBacklogResponse;

@RestController
@RequestMapping("/api/v1/backlog")
@RequiredArgsConstructor
public class WeekBacklogController {

    private final WeekBacklogService weekBacklogService;

    @GetMapping("/week")
    public ResponseEntity<WeekBacklogResponse> current(Authentication auth) {
        return ResponseEntity.ok(weekBacklogService.current(auth.getName()));
    }

    @GetMapping("/week/{year}/{week}")
    public ResponseEntity<WeekBacklogResponse> weekResponse(Authentication auth, @PathVariable int year, @PathVariable int week) {
        return ResponseEntity.ok(weekBacklogService.weekResponse(auth.getName(), year, week));
    }

    @PostMapping("/week/{year}/{week}/delos/{deloId}")
    public ResponseEntity<WeekBacklogResponse> add(Authentication auth, @PathVariable int year, @PathVariable int week, @PathVariable Long deloId) {
        return ResponseEntity.ok(weekBacklogService.add(auth.getName(), year, week, deloId));
    }

    @DeleteMapping("/week/{year}/{week}/delos/{deloId}")
    public ResponseEntity<WeekBacklogResponse> remove(Authentication auth, @PathVariable int year, @PathVariable int week, @PathVariable Long deloId) {
        return ResponseEntity.ok(weekBacklogService.remove(auth.getName(), year, week, deloId));
    }
}
