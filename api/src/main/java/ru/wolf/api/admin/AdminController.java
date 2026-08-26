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
package ru.wolf.api.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.admin.dto.CreateInviteCodeRequest;
import ru.wolf.api.admin.dto.DeleteUserRequest;
import ru.wolf.api.admin.dto.InviteCodeAdminResponse;
import ru.wolf.api.admin.dto.ResetPasswordResponse;
import ru.wolf.api.admin.dto.UserAdminResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    public ResponseEntity<List<UserAdminResponse>> listUsers(
            @RequestParam(defaultValue = "false") boolean includeDemo
    ) {
        return ResponseEntity.ok(adminService.listUsers(includeDemo));
    }

    @PostMapping("/users/{id}/block")
    public ResponseEntity<Void> blockUser(Authentication authentication, @PathVariable Long id) {
        adminService.blockUser(authentication.getName(), id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/users/{id}/unblock")
    public ResponseEntity<Void> unblockUser(Authentication authentication, @PathVariable Long id) {
        adminService.unblockUser(authentication.getName(), id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<ResetPasswordResponse> resetPassword(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(adminService.resetPassword(authentication.getName(), id));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody DeleteUserRequest request
    ) {
        adminService.deleteUser(authentication.getName(), id, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/invite-codes")
    public ResponseEntity<List<InviteCodeAdminResponse>> listInviteCodes() {
        return ResponseEntity.ok(adminService.listInviteCodes());
    }

    @PostMapping("/invite-codes")
    public ResponseEntity<InviteCodeAdminResponse> createInviteCode(
            Authentication authentication,
            @Valid @RequestBody CreateInviteCodeRequest request
    ) {
        return ResponseEntity.ok(adminService.createInviteCode(authentication.getName(), request));
    }

    @PostMapping("/invite-codes/{id}/revoke")
    public ResponseEntity<Void> revokeInviteCode(@PathVariable UUID id) {
        adminService.revokeInviteCode(id);
        return ResponseEntity.ok().build();
    }
}
