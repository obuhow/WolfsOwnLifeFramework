package ru.wolf.api.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalTime;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class UserSettingsController {

    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<UserSettingsResponse> getSettings(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        return ResponseEntity.ok(toResponse(user));
    }

    @PutMapping
    public ResponseEntity<UserSettingsResponse> updateSettings(
            Authentication authentication,
            @Valid @RequestBody UpdateSettingsRequest request
    ) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        user.setTimezone(request.getTimezone());
        user.setNightStart(LocalTime.parse(request.getNightStart()));
        user.setNightEnd(LocalTime.parse(request.getNightEnd()));
        if (request.getDayEnd() != null && !request.getDayEnd().isBlank()) {
            user.setDayEnd(LocalTime.parse(request.getDayEnd()));
        }
        if (request.getDefaultSleepEnd() != null && !request.getDefaultSleepEnd().isBlank()) {
            user.setDefaultSleepEnd(LocalTime.parse(request.getDefaultSleepEnd()));
        }
        user.setHourAccountingMode(request.getHourAccountingMode());
        if (request.getAvailableWeeklyHours() != null) {
            user.setAvailableWeeklyHours(request.getAvailableWeeklyHours());
        }

        userRepository.save(user);

        return ResponseEntity.ok(toResponse(user));
    }

    private static UserSettingsResponse toResponse(User user) {
        return new UserSettingsResponse(
                user.getTimezone(),
                user.getNightStart(),
                user.getNightEnd(),
                user.getDayEnd(),
                user.getDefaultSleepEnd(),
                user.getHourAccountingMode(),
                user.getAvailableWeeklyHours()
        );
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSettingsResponse {
        private String timezone;
        private LocalTime nightStart;
        private LocalTime nightEnd;
        /** Конец дня (граница логических суток), e.g. 02:00 */
        private LocalTime dayEnd;
        /** Конец интервала авто-Сна, e.g. 09:00 */
        private LocalTime defaultSleepEnd;
        private String hourAccountingMode;
        private BigDecimal availableWeeklyHours;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateSettingsRequest {
        @NotBlank
        private String timezone;

        @NotBlank
        private String nightStart;

        @NotBlank
        private String nightEnd;

        /** Optional for backward compat; default 02:00 on entity */
        private String dayEnd;

        private String defaultSleepEnd;

        @NotBlank
        private String hourAccountingMode;

        @DecimalMin(value = "0.0", inclusive = true)
        private BigDecimal availableWeeklyHours;
    }
}