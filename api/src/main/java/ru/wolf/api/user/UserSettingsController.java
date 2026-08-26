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
        if (request.getTimeCaptureMode() != null && !request.getTimeCaptureMode().isBlank()) {
            String mode = request.getTimeCaptureMode().toUpperCase();
            if (!mode.equals("PARALLEL_SLOTS") && !mode.equals("PRIMARY_FOCUS")) {
                throw new IllegalArgumentException("Неизвестный режим фиксации времени");
            }
            user.setTimeCaptureMode(mode);
        }
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
                user.getTimeCaptureMode(),
                user.getAvailableWeeklyHours()
        );
    }

    @Data
    @NoArgsConstructor
    public static class UserSettingsResponse {
        private String timezone;
        private LocalTime nightStart;
        private LocalTime nightEnd;
        /** Конец дня (граница логических суток), e.g. 02:00 */
        private LocalTime dayEnd;
        /** Конец интервала авто-Сна, e.g. 09:00 */
        private LocalTime defaultSleepEnd;
        private String hourAccountingMode;
        private String timeCaptureMode;
        private BigDecimal availableWeeklyHours;

        public UserSettingsResponse(String timezone, LocalTime nightStart, LocalTime nightEnd,
                                    LocalTime dayEnd, LocalTime defaultSleepEnd,
                                    String hourAccountingMode, String timeCaptureMode,
                                    BigDecimal availableWeeklyHours) {
            this.timezone = timezone;
            this.nightStart = nightStart;
            this.nightEnd = nightEnd;
            this.dayEnd = dayEnd;
            this.defaultSleepEnd = defaultSleepEnd;
            this.hourAccountingMode = hourAccountingMode;
            this.timeCaptureMode = timeCaptureMode;
            this.availableWeeklyHours = availableWeeklyHours;
        }
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

        private String timeCaptureMode;

        @DecimalMin(value = "0.0", inclusive = true)
        private BigDecimal availableWeeklyHours;
    }
}