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
package ru.wolf.api.instance;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.wolf.api.instance.dto.InstanceRegistrationResponse;
import ru.wolf.api.instance.dto.UpdateInviteAccessRequest;

/**
 * Глобальные настройки экземпляра (release 1.1, тикет 08).
 * <ul>
 *   <li>{@code GET /api/v1/instance/registration} — permitAll: анонимный логин-экран
 *       решает, показывать ли ссылку «У меня есть код».</li>
 *   <li>{@code PUT /api/v1/instance/invite-access} — только ADMIN: переключатель
 *       в Настройках.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/instance")
@RequiredArgsConstructor
public class InstanceController {

    private final InstanceConfigService instanceConfigService;

    @GetMapping("/registration")
    public ResponseEntity<InstanceRegistrationResponse> registration() {
        return ResponseEntity.ok(new InstanceRegistrationResponse(instanceConfigService.isInviteAccessOpen()));
    }

    @PutMapping("/invite-access")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InstanceRegistrationResponse> updateInviteAccess(
            @RequestBody UpdateInviteAccessRequest request
    ) {
        boolean open = instanceConfigService.setInviteAccessOpen(request.inviteAccessOpen());
        return ResponseEntity.ok(new InstanceRegistrationResponse(open));
    }
}
