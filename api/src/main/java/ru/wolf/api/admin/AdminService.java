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

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.admin.dto.CreateInviteCodeRequest;
import ru.wolf.api.admin.dto.DeleteUserRequest;
import ru.wolf.api.admin.dto.InviteCodeAdminResponse;
import ru.wolf.api.admin.dto.ResetPasswordResponse;
import ru.wolf.api.admin.dto.UserAdminResponse;
import ru.wolf.api.invite.InviteCode;
import ru.wolf.api.invite.InviteCodeRepository;
import ru.wolf.api.invite.InviteService;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.lifesphere.LifeSphereRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserPurgeService;
import ru.wolf.api.user.UserRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final InviteService inviteService;
    private final PasswordEncoder passwordEncoder;
    private final UserPurgeService userPurgeService;
    private final LifeAreaRepository lifeAreaRepository;
    private final LifeSphereRepository lifeSphereRepository;

    @Transactional(readOnly = true)
    public List<UserAdminResponse> listUsers(boolean includeDemo) {
        List<User> users = includeDemo
                ? userRepository.findAllUsersIncludeDemo()
                : userRepository.findAllRegularUsers();
        return users.stream().map(UserAdminResponse::from).collect(Collectors.toList());
    }

    @Transactional
    public void blockUser(String currentUsername, Long id) {
        User currentUser = requireCurrent(currentUsername);
        User target = requireTarget(id);
        if (currentUser.getId().equals(id)) {
            throw new IllegalArgumentException("Нельзя заблокировать себя");
        }
        if ("BLOCKED".equals(target.getStatus())) {
            throw new IllegalArgumentException("Пользователь уже заблокирован");
        }
        target.setStatus("BLOCKED");
        userRepository.save(target);
    }

    @Transactional
    public void unblockUser(String currentUsername, Long id) {
        User currentUser = requireCurrent(currentUsername);
        User target = requireTarget(id);
        if (currentUser.getId().equals(id)) {
            throw new IllegalArgumentException("Нельзя разблокировать себя");
        }
        if ("ACTIVE".equals(target.getStatus())) {
            throw new IllegalArgumentException("Пользователь уже активен");
        }
        target.setStatus("ACTIVE");
        userRepository.save(target);
    }

    @Transactional
    public ResetPasswordResponse resetPassword(String currentUsername, Long id) {
        User currentUser = requireCurrent(currentUsername);
        User target = requireTarget(id);
        if (currentUser.getId().equals(id)) {
            throw new IllegalArgumentException("Нельзя сбросить пароль себе");
        }
        String tempPassword = generateTempPassword();
        target.setPasswordHash(passwordEncoder.encode(tempPassword));
        userRepository.save(target);
        return new ResetPasswordResponse(tempPassword);
    }

    @Transactional
    public void deleteUser(String currentUsername, Long id, DeleteUserRequest request) {
        User currentUser = requireCurrent(currentUsername);
        if (currentUser.getId().equals(id)) {
            throw new IllegalArgumentException("Нельзя удалить себя");
        }
        long activeAdminCount = userRepository.countByRoleAndStatus("ADMIN", "ACTIVE");
        User target = requireTarget(id);
        if ("ADMIN".equals(target.getRole()) && "ACTIVE".equals(target.getStatus()) && activeAdminCount <= 1) {
            throw new IllegalArgumentException("Нельзя удалить последнего активного администратора");
        }
        if (!target.getUsername().equals(request.confirmUsername())) {
            throw new IllegalArgumentException("Имя пользователя не совпадает");
        }
        if ("DEMO".equals(target.getAccountType())) {
            // Демо-профиль содержит связанные данные. Сначала чистим их в FK-порядке,
            // затем удаляем пользовательские области/сферы и сам аккаунт.
            userPurgeService.purgeProfileData(target);
            lifeAreaRepository.deleteAllByUser(target);
            lifeSphereRepository.deleteAllByUser(target);
        }
        userRepository.delete(target);
    }

    @Transactional(readOnly = true)
    public List<InviteCodeAdminResponse> listInviteCodes() {
        List<InviteCode> codes = inviteCodeRepository.findAllByOrderByCreatedAtDesc();
        return codes.stream().map(InviteCodeAdminResponse::from).collect(Collectors.toList());
    }

    @Transactional
    public InviteCodeAdminResponse createInviteCode(String currentUsername, CreateInviteCodeRequest request) {
        User createdBy = requireCurrent(currentUsername);
        InviteCode invite = inviteService.createInvite(
                createdBy,
                request.maxUses() != null ? request.maxUses() : 1,
                request.expiresAt(),
                request.note()
        );
        return InviteCodeAdminResponse.from(invite);
    }

    @Transactional
    public void revokeInviteCode(UUID id) {
        InviteCode invite = inviteCodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invite code not found"));
        if (invite.getRevokedAt() == null) {
            invite.setRevokedAt(java.time.Instant.now());
            inviteCodeRepository.save(invite);
        }
    }

    private User requireCurrent(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Current user not found"));
    }

    private User requireTarget(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private String generateTempPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(12);
        java.security.SecureRandom random = new java.security.SecureRandom();
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
