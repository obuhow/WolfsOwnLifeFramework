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
package ru.wolf.api.auth;

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
import ru.wolf.api.auth.dto.AuthRequest;
import ru.wolf.api.auth.dto.AuthResponse;
import ru.wolf.api.auth.dto.MeResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (DisabledException | AccountExpiredException | LockedException e) {
            // Returned directly (not thrown) to avoid Tomcat's ERROR-dispatch re-entering the
            // security filter chain on the non-permitAll "/error" path, which would downgrade
            // this to 403 via the default AuthenticationEntryPoint.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Аккаунт недоступен"));
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        ru.wolf.api.user.User user = authService.currentUserAfterLogin(userDetails.getUsername());

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
        return ResponseEntity.ok(authService.me(authentication.getName()));
    }
}
