package ru.wolf.api.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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

        return ResponseEntity.ok(new UserSettingsResponse(
                user.getTimezone(),
                user.getNightStart(),
                user.getNightEnd(),
                user.getHourAccountingMode()
        ));
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
        user.setHourAccountingMode(request.getHourAccountingMode());

        userRepository.save(user);

        return ResponseEntity.ok(new UserSettingsResponse(
                user.getTimezone(),
                user.getNightStart(),
                user.getNightEnd(),
                user.getHourAccountingMode()
        ));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSettingsResponse {
        private String timezone;
        private LocalTime nightStart;
        private LocalTime nightEnd;
        private String hourAccountingMode;
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

        @NotBlank
        private String hourAccountingMode;
    }
}