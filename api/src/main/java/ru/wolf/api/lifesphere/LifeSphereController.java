package ru.wolf.api.lifesphere;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.lifesphere.dto.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/life-spheres")
@RequiredArgsConstructor
public class LifeSphereController {
    private final LifeSphereService lifeSphereService;
    @GetMapping public ResponseEntity<List<LifeSphereResponse>> getLifeSpheres(Authentication a){return ResponseEntity.ok(lifeSphereService.list(a.getName()));}
    @PostMapping public ResponseEntity<LifeSphereResponse> createLifeSphere(Authentication a,@Valid @RequestBody CreateLifeSphereRequest r){return ResponseEntity.ok(lifeSphereService.create(a.getName(),r));}
    @PutMapping("/{id}") public ResponseEntity<LifeSphereResponse> updateLifeSphere(Authentication a,@PathVariable Long id,@Valid @RequestBody UpdateLifeSphereRequest r){return ResponseEntity.ok(lifeSphereService.update(a.getName(),id,r));}
    @DeleteMapping("/{id}") public ResponseEntity<Void> deleteLifeSphere(Authentication a,@PathVariable Long id){lifeSphereService.delete(a.getName(),id);return ResponseEntity.noContent().build();}
    @PutMapping("/{id}/archive") public ResponseEntity<LifeSphereResponse> archiveLifeSphere(Authentication a,@PathVariable Long id){return ResponseEntity.ok(lifeSphereService.archive(a.getName(),id));}
    @PutMapping("/{id}/move") public ResponseEntity<LifeSphereResponse> moveLifeSphere(Authentication a,@PathVariable Long id,@Valid @RequestBody MoveLifeSphereRequest r){return ResponseEntity.ok(lifeSphereService.move(a.getName(),id,r));}
}
