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
 * but WITHOUT ANY WARRANTY; without even implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.loadcharts;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.wolf.api.loadcharts.dto.LoadChartsResponse;

@RestController
@RequestMapping("/api/v1/planning")
@RequiredArgsConstructor
public class LoadChartsController {

    private final LoadChartsService service;

    /** Единый источник чисел для четырёх диаграмм нагрузки (release 0.8). */
    @GetMapping("/load-charts")
    public ResponseEntity<LoadChartsResponse> loadCharts(
            Authentication authentication,
            @RequestParam(required = false) Integer horizonMonths,
            @RequestParam(required = false) String lifeAreaIds) {
        return ResponseEntity.ok(service.loadCharts(authentication.getName(), horizonMonths, lifeAreaIds));
    }
}
