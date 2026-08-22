package ru.wolf.api.user;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "\"user\"")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String timezone = "Europe/Moscow";

    @Builder.Default
    @Column(name = "night_start", nullable = false)
    private java.time.LocalTime nightStart = java.time.LocalTime.of(23, 0);

    @Builder.Default
    @Column(name = "night_end", nullable = false)
    private java.time.LocalTime nightEnd = java.time.LocalTime.of(7, 0);

    /** Logical day rolls at this wall-clock time (may be after midnight for prior calendar date). */
    @Builder.Default
    @Column(name = "day_end", nullable = false)
    private java.time.LocalTime dayEnd = java.time.LocalTime.of(2, 0);

    /** Default «Сон» interval ends at this clock time on the calendar date of day start. */
    @Builder.Default
    @Column(name = "default_sleep_end", nullable = false)
    private java.time.LocalTime defaultSleepEnd = java.time.LocalTime.of(9, 0);

    @Builder.Default
    @Column(name = "hour_accounting_mode", nullable = false, length = 20)
    private String hourAccountingMode = "PRIMARY_ONLY";

    @Builder.Default
    @Column(name = "time_capture_mode", nullable = false, length = 20)
    private String timeCaptureMode = "PARALLEL_SLOTS";

    @Builder.Default
    @Column(name = "available_weekly_hours", nullable = false, precision = 6, scale = 2)
    private java.math.BigDecimal availableWeeklyHours = java.math.BigDecimal.valueOf(30);

    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private java.time.Instant updatedAt;

    /** USER | ADMIN */
    @Builder.Default
    @Column(nullable = false, length = 20)
    private String role = "USER";

    /** ACTIVE | BLOCKED */
    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    /** REGULAR | DEMO */
    @Builder.Default
    @Column(name = "account_type", nullable = false, length = 20)
    private String accountType = "REGULAR";

    @Column(length = 255)
    private String email;

    /** Only set for DEMO accounts; null means the account never expires. */
    @Column(name = "expires_at")
    private java.time.Instant expiresAt;

    /** Set once the first-run wizard is completed or skipped. */
    @Column(name = "onboarding_completed_at")
    private java.time.Instant onboardingCompletedAt;

    @Column(name = "last_login_at")
    private java.time.Instant lastLoginAt;

    @PrePersist
    void onCreate() {
        this.createdAt = java.time.Instant.now();
        this.updatedAt = java.time.Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = java.time.Instant.now();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return expiresAt == null || expiresAt.isAfter(java.time.Instant.now());
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "ACTIVE".equals(status);
    }
}