package ru.wolf.api.morning;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.morning.dto.MorningDigestResponse;

@RestController
@RequestMapping("/api/v1/morning-digest")
@RequiredArgsConstructor
public class MorningDigestController {
 private final MorningDigestService morningDigestService;
 @GetMapping public ResponseEntity<MorningDigestResponse> get(Authentication authentication) { return ResponseEntity.ok(morningDigestService.build(authentication.getName())); }
}
