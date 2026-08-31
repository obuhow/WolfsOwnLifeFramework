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
 * GNU Affero General License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. if not, see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.max;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.wolf.api.max.dto.MaxDisconnectRequest;
import ru.wolf.api.max.dto.MaxLinkStatus;
import ru.wolf.api.user.User;

/**
 * Authenticated endpoints for the Settings "Импорт из Max" block (release 0.7,
 * ticket 04, point 2): issue a one-time link token, read current link state, and
 * unlink. Unlike the webhook, these require the normal JWT session.
 */
@RestController
@RequestMapping("/api/v1/bot/max/link")
@RequiredArgsConstructor
public class MaxLinkController {

    private final MaxLinkService linkService;

    @GetMapping
    public ResponseEntity<MaxLinkStatus> status(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(linkService.getStatus(user.getUsername()));
    }

    @PostMapping
    public ResponseEntity<MaxLinkStatus> issue(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(linkService.issueToken(user.getUsername()));
    }

    @PostMapping("/disconnect")
    public ResponseEntity<Void> disconnect(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody MaxDisconnectRequest request
    ) {
        linkService.unlink(user.getUsername());
        return ResponseEntity.ok().build();
    }
}
