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

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.wolf.api.max.dto.MaxUpdate;

/**
 * Max webhook ingress (release 0.7, ticket 04, point 3). The endpoint is
 * intentionally outside the JWT-authenticated surface: it is guarded only by the
 * dedicated {@code X-Max-Bot-Api-Secret} header, and the sender's WOLF identity is
 * resolved later from the {@code max_link} table by {@code chat_id} — never from
 * the request. An invalid/missing secret yields 401; a well-formed update is
 * always acknowledged with 200 so Max does not retry.
 */
@RestController
@RequestMapping("/api/v1/bot/max")
@RequiredArgsConstructor
public class MaxWebhookController {

    static final String SECRET_HEADER = "X-Max-Bot-Api-Secret";

    private final MaxProperties properties;
    private final MaxImportService importService;

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestHeader(value = SECRET_HEADER, required = false) String secret,
            @RequestBody MaxUpdate update,
            HttpServletRequest request
    ) {
        if (!isAuthorized(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        importService.handleUpdate(update);
        return ResponseEntity.ok().build();
    }

    private boolean isAuthorized(String secret) {
        String expected = properties.getSecretToken();
        if (expected == null || expected.isBlank()) {
            // Not configured → reject until an operator sets wolf.max.secret-token.
            return false;
        }
        return expected.equals(secret);
    }
}
