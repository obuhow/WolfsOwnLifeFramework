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
package ru.wolf.api.onboarding;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.onboarding.dto.*;

@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    @PostMapping("/step1/project")
    public ResponseEntity<Step1Response> step1Project(
            Authentication authentication,
            @Valid @RequestBody Step1Request request
    ) {
        return ResponseEntity.ok(onboardingService.step1Project(authentication.getName(), request));
    }

    @PostMapping("/step2/goal")
    public ResponseEntity<Step2Response> step2Goal(
            Authentication authentication,
            @Valid @RequestBody Step2Request request
    ) {
        return ResponseEntity.ok(onboardingService.step2Goal(authentication.getName(), request));
    }

    @PostMapping("/step3/weekly-hours")
    public ResponseEntity<Step3Response> step3WeeklyHours(
            Authentication authentication,
            @Valid @RequestBody Step3Request request
    ) {
        return ResponseEntity.ok(onboardingService.step3WeeklyHours(authentication.getName(), request));
    }

    @PostMapping("/complete")
    public ResponseEntity<CompleteResponse> complete(Authentication authentication) {
        return ResponseEntity.ok(onboardingService.complete(authentication.getName()));
    }

    @PostMapping("/skip")
    public ResponseEntity<CompleteResponse> skip(Authentication authentication) {
        return ResponseEntity.ok(onboardingService.skip(authentication.getName()));
    }

    @GetMapping("/status")
    public ResponseEntity<StatusResponse> status(Authentication authentication) {
        return ResponseEntity.ok(onboardingService.status(authentication.getName()));
    }

    @PostMapping("/load-profile")
    public ResponseEntity<LoadProfileResponse> loadProfile(
            Authentication authentication,
            @Valid @RequestBody LoadProfileRequest request
    ) {
        return ResponseEntity.ok(onboardingService.loadProfile(authentication.getName(), request));
    }

    @PostMapping("/reload-profile")
    public ResponseEntity<LoadProfileResponse> reloadProfile(
            Authentication authentication,
            @Valid @RequestBody LoadProfileRequest request
    ) {
        return ResponseEntity.ok(onboardingService.reloadProfile(authentication.getName(), request));
    }

    @PostMapping("/purge-profile")
    public ResponseEntity<PurgeProfileResponse> purgeProfile(Authentication authentication) {
        return ResponseEntity.ok(onboardingService.purgeProfile(authentication.getName()));
    }
}
