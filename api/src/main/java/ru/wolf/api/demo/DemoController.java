package ru.wolf.api.demo;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.auth.JwtUtil;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/v1/demo")
@RequiredArgsConstructor
@Slf4j
public class DemoController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final DemoFixtureGenerator demoFixtureGenerator;

    @Value("${wolf.demo.rate-per-ip-per-hour:3}")
    private int ratePerIpPerHour;

    @Value("${wolf.demo.max-live-accounts:200}")
    private int maxLiveAccounts;

    @Value("${wolf.demo.ttl-hours:24}")
    private int ttlHours;

    // Simple in-memory rate limiter (per IP per hour)
    private final Map<String, IpRateLimit> ipRateLimits = new ConcurrentHashMap<>();

    private static class IpRateLimit {
        long windowStart;
        AtomicInteger count = new AtomicInteger(0);
    }

    @PostMapping("/session")
    @PreAuthorize("permitAll()")
    @Transactional
    public ResponseEntity<?> createDemoSession(HttpServletRequest request) {
        String clientIp = getClientIp(request);
        
        // Rate limiting
        if (!checkRateLimit(clientIp)) {
            log.warn("Demo rate limit exceeded for IP: {}", clientIp);
            return ResponseEntity.status(429)
                    .body(Map.of("message", "Демо временно недоступно, попробуйте позже"));
        }

        // Check max live demo accounts
        long liveDemoCount = userRepository.countByAccountTypeAndStatus("DEMO", "ACTIVE");
        if (liveDemoCount >= maxLiveAccounts) {
            log.warn("Max live demo accounts reached: {}", liveDemoCount);
            return ResponseEntity.status(429)
                    .body(Map.of("message", "Демо временно недоступно, попробуйте позже"));
        }

        // Create demo user
        String username = "demo-" + UUID.randomUUID().toString().substring(0, 8);
        String rawPassword = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        
        User demoUser = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role("USER")
                .status("ACTIVE")
                .accountType("DEMO")
                .expiresAt(Instant.now().plusSeconds(ttlHours * 3600L))
                .onboardingCompletedAt(Instant.now())
                .timezone("Europe/Moscow")
                .nightStart(java.time.LocalTime.of(23, 0))
                .nightEnd(java.time.LocalTime.of(7, 0))
                .dayEnd(java.time.LocalTime.of(2, 0))
                .defaultSleepEnd(java.time.LocalTime.of(9, 0))
                .hourAccountingMode("PRIMARY_ONLY")
                .timeCaptureMode("PARALLEL_SLOTS")
                .availableWeeklyHours(java.math.BigDecimal.valueOf(30))
                .build();
        
        demoUser = userRepository.save(demoUser);

        // Generate fixtures
        demoFixtureGenerator.populate(demoUser);

        // Generate JWT
        String token = jwtUtil.generateToken(
                demoUser.getUsername(), 
                demoUser.getId(), 
                demoUser.getRole(),
                ttlHours * 3600L * 1000 // TTL in milliseconds
        );

        log.info("Created demo session for user: {}", username);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "expiresAt", demoUser.getExpiresAt(),
                "username", demoUser.getUsername()
        ));
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean checkRateLimit(String ip) {
        long now = System.currentTimeMillis();
        long hourMs = 3600_000L;
        
        IpRateLimit limit = ipRateLimits.computeIfAbsent(ip, k -> new IpRateLimit());
        synchronized (limit) {
            if (limit.windowStart == 0 || now - limit.windowStart >= hourMs) {
                limit.windowStart = now;
                limit.count.set(0);
            }
            if (limit.count.incrementAndGet() > ratePerIpPerHour) {
                return false;
            }
            return true;
        }
    }
}