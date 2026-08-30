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
package ru.wolf.api.demo;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.wolf.api.demo.dto.DemoProfileOption;
import ru.wolf.api.demo.dto.DemoRegisterRequest;
import ru.wolf.api.demo.dto.DemoRegisterResponse;

import java.util.List;

/**
 * Public (permitAll via {@code /api/v1/demo/**}) demo entry points for the login screen
 * "Демо-режим" button (release 1.0, ticket 07): list the three profiles and spin up a demo
 * account. No authentication required — an anonymous guest becomes a fresh isolated demo user.
 */
@RestController
@RequestMapping("/api/v1/demo")
@RequiredArgsConstructor
public class DemoPublicController {

    private final DemoRegisterService demoRegisterService;

    @GetMapping("/profiles")
    public ResponseEntity<List<DemoProfileOption>> profiles() {
        return ResponseEntity.ok(List.of(
                new DemoProfileOption("worker-class", "Рабочий класс"),
                new DemoProfileOption("wise-freelancer", "Мудрый фрилансер"),
                new DemoProfileOption("free-artist", "Свободный художник")
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<DemoRegisterResponse> register(@Valid @RequestBody DemoRegisterRequest request) {
        return ResponseEntity.ok(demoRegisterService.register(request.profileSlug()));
    }
}
