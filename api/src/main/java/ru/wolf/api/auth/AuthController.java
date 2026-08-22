package ru.wolf.api.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final ru.wolf.api.user.UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (DisabledException | AccountExpiredException | LockedException e) {
            // Returned directly (not thrown) to avoid Tomcat's ERROR-dispatch re-entering the
            // security filter chain on the non-permitAll "/error" path, which would downgrade
            // this to 403 via the default AuthenticationEntryPoint.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Аккаунт недоступен"));
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        ru.wolf.api.user.User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new IllegalStateException("User not found after authentication"));

        user.setLastLoginAt(java.time.Instant.now());
        userRepository.save(user);

        String token;
        if ("DEMO".equals(user.getAccountType()) && user.getExpiresAt() != null) {
            long ttlMs = Math.max(0, user.getExpiresAt().toEpochMilli() - System.currentTimeMillis());
            token = jwtUtil.generateToken(userDetails.getUsername(), user.getId(), user.getRole(), ttlMs);
        } else {
            token = jwtUtil.generateToken(userDetails.getUsername(), user.getId(), user.getRole());
        }

        return ResponseEntity.ok(new AuthResponse(token));
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        ru.wolf.api.user.User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
        return ResponseEntity.ok(new MeResponse(
                user.getUsername(),
                user.getRole(),
                user.getAccountType(),
                user.getEmail(),
                user.getOnboardingCompletedAt() != null,
                user.getExpiresAt()
        ));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthRequest {
        @NotBlank
        private String username;

        @NotBlank
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthResponse {
        private String token;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MeResponse {
        private String username;
        private String role;
        private String accountType;
        private String email;
        private boolean onboardingCompleted;
        private java.time.Instant expiresAt;
    }
}