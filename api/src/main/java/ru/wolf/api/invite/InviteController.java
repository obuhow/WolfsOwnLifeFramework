package ru.wolf.api.invite;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;
    private final UserRepository userRepository;

    @PostMapping("/invite")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createInvite(@AuthenticationPrincipal UserDetails userDetails,
                                          @Valid @RequestBody CreateInviteRequest request) {
        User createdBy = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new IllegalStateException("User not found"));
        InviteCode invite = inviteService.createInvite(
                createdBy,
                request.maxUses != null ? request.maxUses : 1,
                request.expiresAt,
                request.note
        );
        return ResponseEntity.ok(Map.of("code", invite.getCode()));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            var tokenOpt = inviteService.registerWithInvite(
                    request.code,
                    request.username,
                    request.password
            );
            if (tokenOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Код недействителен"));
            }
            return ResponseEntity.ok(Map.of("token", tokenOpt.get()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Код недействителен"));
        }
    }

    public static class CreateInviteRequest {
        public Integer maxUses;
        public Instant expiresAt;
        public String note;
    }

    public static class RegisterRequest {
        @NotBlank
        public String code;
        @NotBlank
        public String username;
        @NotBlank
        public String password;
    }
}