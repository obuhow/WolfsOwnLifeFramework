package ru.wolf.api.focus;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.focus.dto.*;

@RestController @RequestMapping("/api/v1/focus") @RequiredArgsConstructor
public class FocusController {
    private final FocusService service;

    private ResponseEntity<?> response(FocusService.Result result) {
        return ResponseEntity.status(result.status()).body(result.body());
    }
    @PostMapping("/start") public ResponseEntity<?> start(Authentication a, @RequestBody StartRequest r) { return response(service.start(a.getName(), r)); }
    @GetMapping("/current") public ResponseEntity<?> currentSession(Authentication a) { return response(service.currentSession(a.getName())); }
    @GetMapping("/{id}") public ResponseEntity<?> details(Authentication a, @PathVariable Long id) { return response(service.details(a.getName(), id)); }
    @DeleteMapping("/{sessionId}/distractions/{distractionId}")
    public ResponseEntity<Void> deleteDistraction(Authentication a, @PathVariable Long sessionId, @PathVariable Long distractionId) {
        service.deleteDistraction(a.getName(), sessionId, distractionId);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/{id}/stop") public ResponseEntity<?> stop(Authentication a, @PathVariable Long id, @RequestBody(required=false) StopRequest r) { return response(service.stop(a.getName(), id, r)); }
    @PostMapping("/{id}/distractions") public ResponseEntity<?> distraction(Authentication a, @PathVariable Long id, @RequestBody DistractionRequest r) { return response(service.distraction(a.getName(), id, r)); }
}
