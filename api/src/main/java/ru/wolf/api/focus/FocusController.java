package ru.wolf.api.focus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.focus.dto.*;
@RestController @RequestMapping("/api/v1/focus") @RequiredArgsConstructor
public class FocusController {
 private final FocusService service;
 @PostMapping("/start") public ResponseEntity<?> start(Authentication a,@RequestBody StartRequest r){return service.start(a,r);}
 @GetMapping("/current") public ResponseEntity<?> currentSession(Authentication a){return service.currentSession(a);}
 @GetMapping("/{id}") public ResponseEntity<?> details(Authentication a,@PathVariable Long id){return service.details(a,id);}
 @DeleteMapping("/{sessionId}/distractions/{distractionId}") public ResponseEntity<Void> deleteDistraction(Authentication a,@PathVariable Long sessionId,@PathVariable Long distractionId){return service.deleteDistraction(a,sessionId,distractionId);}
 @PostMapping("/{id}/stop") public ResponseEntity<?> stop(Authentication a,@PathVariable Long id,@RequestBody(required=false) StopRequest r){return service.stop(a,id,r);}
 @PostMapping("/{id}/distractions") public ResponseEntity<?> distraction(Authentication a,@PathVariable Long id,@RequestBody DistractionRequest r){return service.distraction(a,id,r);}
}
