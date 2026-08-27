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
package ru.wolf.api.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.user.dto.UpdateSettingsRequest;
import ru.wolf.api.user.dto.UserSettingsResponse;

import java.math.BigDecimal;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserSettingsResponse getSettings(String username) {
        User user = currentUser(username);
        return UserSettingsResponse.from(user);
    }

    @Transactional
    public UserSettingsResponse updateSettings(String username, UpdateSettingsRequest request) {
        User user = currentUser(username);

        user.setTimezone(request.timezone());
        user.setNightStart(LocalTime.parse(request.nightStart()));
        user.setNightEnd(LocalTime.parse(request.nightEnd()));
        if (request.dayEnd() != null && !request.dayEnd().isBlank()) {
            user.setDayEnd(LocalTime.parse(request.dayEnd()));
        }
        if (request.defaultSleepEnd() != null && !request.defaultSleepEnd().isBlank()) {
            user.setDefaultSleepEnd(LocalTime.parse(request.defaultSleepEnd()));
        }
        user.setHourAccountingMode(request.hourAccountingMode());
        if (request.timeCaptureMode() != null && !request.timeCaptureMode().isBlank()) {
            String mode = request.timeCaptureMode().toUpperCase();
            if (!mode.equals("PARALLEL_SLOTS") && !mode.equals("PRIMARY_FOCUS")) {
                throw new IllegalArgumentException("Неизвестный режим фиксации времени");
            }
            user.setTimeCaptureMode(mode);
        }
        if (request.availableWeeklyHours() != null) {
            user.setAvailableWeeklyHours(request.availableWeeklyHours());
        }

        return UserSettingsResponse.from(userRepository.save(user));
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }
}
