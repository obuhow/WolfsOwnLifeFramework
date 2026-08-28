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
package ru.wolf.api.importer;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.wolf.api.importer.dto.ConfirmImportRequest;
import ru.wolf.api.importer.dto.ConfirmImportResponse;
import ru.wolf.api.importer.dto.ParseImportRequest;
import ru.wolf.api.importer.dto.ParseResult;
import ru.wolf.api.user.User;

/**
 * Chat-panel import channel (release 0.7, ticket 02).
 *
 * <p>Two endpoints over the shared {@link ImportParserService}: parse free text into
 * candidate entities (with per-field confidence), and confirm the user-edited
 * candidates into real WOLF entities. The controller is HTTP-only — no
 * {@code Repository}, no business logic, no web annotations in the services it calls.
 *
 * <p>The authenticated {@link User} is supplied by Spring Security (the app's
 * {@code UserDetailsServiceImpl} returns the {@code User} entity, which implements
 * {@code UserDetails}); this is infrastructure, not a repository injection.
 */
@RestController
@RequestMapping("/api/v1/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportParserService parserService;
    private final ImportConfirmService confirmService;

    @PostMapping("/parse")
    public ResponseEntity<ParseResult> parse(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ParseImportRequest request
    ) {
        ParseResult result = parserService.parse(user, request.text());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/confirm")
    public ResponseEntity<ConfirmImportResponse> confirm(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ConfirmImportRequest request
    ) {
        return ResponseEntity.ok(confirmService.confirm(user.getUsername(), request));
    }
}
