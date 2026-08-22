package ru.wolf.api.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.invite.InviteCode;
import ru.wolf.api.invite.InviteCodeRepository;
import ru.wolf.api.invite.InviteService;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final InviteService inviteService;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/users")
    public ResponseEntity<List<UserAdminResponse>> listUsers(
            @RequestParam(defaultValue = "false") boolean includeDemo
    ) {
        List<User> users = includeDemo 
                ? userRepository.findAllUsersIncludeDemo()
                : userRepository.findAllRegularUsers();
        
        return ResponseEntity.ok(users.stream().map(this::toAdminResponse).collect(Collectors.toList()));
    }

    @PostMapping("/users/{id}/block")
    @Transactional
    public ResponseEntity<Void> blockUser(
            Authentication authentication,
            @PathVariable Long id
    ) {
        User currentUser = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Current user not found"));
        
        if (currentUser.getId().equals(id)) {
            return ResponseEntity.badRequest().build();
        }
        
        User target = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if ("BLOCKED".equals(target.getStatus())) {
            return ResponseEntity.badRequest().build();
        }
        target.setStatus("BLOCKED");
        userRepository.save(target);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/users/{id}/unblock")
    @Transactional
    public ResponseEntity<Void> unblockUser(
            Authentication authentication,
            @PathVariable Long id
    ) {
        User currentUser = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Current user not found"));
        
        if (currentUser.getId().equals(id)) {
            return ResponseEntity.badRequest().build();
        }
        
        User target = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if ("ACTIVE".equals(target.getStatus())) {
            return ResponseEntity.badRequest().build();
        }
        target.setStatus("ACTIVE");
        userRepository.save(target);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/users/{id}/reset-password")
    @Transactional
    public ResponseEntity<ResetPasswordResponse> resetPassword(
            Authentication authentication,
            @PathVariable Long id
    ) {
        User currentUser = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Current user not found"));
        
        if (currentUser.getId().equals(id)) {
            return ResponseEntity.badRequest().build();
        }
        
        User target = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        String tempPassword = generateTempPassword();
        target.setPasswordHash(passwordEncoder.encode(tempPassword));
        userRepository.save(target);
        
        return ResponseEntity.ok(new ResetPasswordResponse(tempPassword));
    }

    @DeleteMapping("/users/{id}")
    @Transactional
    public ResponseEntity<Void> deleteUser(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody DeleteUserRequest request
    ) {
        User currentUser = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Current user not found"));
        
        if (currentUser.getId().equals(id)) {
            return ResponseEntity.badRequest().build();
        }
        
        // Check if this is the last active admin
        long activeAdminCount = userRepository.countByRoleAndStatus("ADMIN", "ACTIVE");
        if (activeAdminCount <= 1) {
            User target = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
            if ("ADMIN".equals(target.getRole()) && "ACTIVE".equals(target.getStatus())) {
                return ResponseEntity.badRequest().build(); // Cannot delete last active admin
            }
        }
        
        // Verify username confirmation
        User target = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!target.getUsername().equals(request.confirmUsername())) {
            return ResponseEntity.badRequest().build();
        }
        
        // Note: In a production system, we'd use a proper UserPurgeService.
        // For now, rely on DB ON DELETE CASCADE for most entities.
        // The User entity deletion will cascade to entities with proper FK constraints.
        userRepository.delete(target);
        
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/invite-codes")
    @Transactional(readOnly = true)
    public ResponseEntity<List<InviteCodeAdminResponse>> listInviteCodes() {
        List<InviteCode> codes = inviteCodeRepository.findAllByOrderByCreatedAtDesc();
        return ResponseEntity.ok(codes.stream().map(this::toInviteAdminResponse).collect(Collectors.toList()));
    }

    @PostMapping("/invite-codes")
    @Transactional
    public ResponseEntity<InviteCodeAdminResponse> createInviteCode(
            Authentication authentication,
            @RequestBody CreateInviteCodeRequest request
    ) {
        User createdBy = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
        
        InviteCode invite = inviteService.createInvite(
                createdBy,
                request.maxUses() != null ? request.maxUses() : 1,
                request.expiresAt(),
                request.note()
        );
        
        return ResponseEntity.ok(toInviteAdminResponse(invite));
    }

    @PostMapping("/invite-codes/{id}/revoke")
    @Transactional
    public ResponseEntity<Void> revokeInviteCode(@PathVariable java.util.UUID id) {
        InviteCode invite = inviteCodeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Invite code not found"));
        if (invite.getRevokedAt() == null) {
            invite.setRevokedAt(java.time.Instant.now());
            inviteCodeRepository.save(invite);
        }
        return ResponseEntity.ok().build();
    }

    private UserAdminResponse toAdminResponse(User user) {
        // Count user's data - use repository queries instead of lazy collections
        // For now return 0 counts - can be enhanced later with specific count queries
        return new UserAdminResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getAccountType(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                0,
                0,
                0
        );
    }

    private InviteCodeAdminResponse toInviteAdminResponse(InviteCode code) {
        return new InviteCodeAdminResponse(
                code.getId(),
                code.getCode(),
                code.getCreatedBy().getUsername(),
                code.getMaxUses(),
                code.getUsedCount(),
                code.getExpiresAt(),
                code.getRevokedAt(),
                code.getNote(),
                code.getCreatedAt()
        );
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserAdminResponse {
        private Long id;
        private String username;
        private String email;
        private String role;
        private String status;
        private String accountType;
        private java.time.Instant createdAt;
        private java.time.Instant lastLoginAt;
        private long projectCount;
        private long deloCount;
        private long timeEntryCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InviteCodeAdminResponse {
        private java.util.UUID id;
        private String code;
        private String createdByUsername;
        private Integer maxUses;
        private Integer usedCount;
        private java.time.Instant expiresAt;
        private java.time.Instant revokedAt;
        private String note;
        private java.time.Instant createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResetPasswordResponse {
        private String tempPassword;
    }

    public record CreateInviteCodeRequest(
            Integer maxUses,
            java.time.Instant expiresAt,
            String note
    ) {}

    public record DeleteUserRequest(
            @NotBlank String confirmUsername
    ) {}
}