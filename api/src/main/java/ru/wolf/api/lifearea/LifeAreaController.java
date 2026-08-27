package ru.wolf.api.lifearea;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.lifearea.dto.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/life-areas")
@RequiredArgsConstructor
public class LifeAreaController {
    private final LifeAreaService lifeAreaService;
    @GetMapping public ResponseEntity<List<LifeAreaResponse>> getLifeAreas(Authentication a){return ResponseEntity.ok(lifeAreaService.list(a.getName()));}
    @PostMapping public ResponseEntity<LifeAreaResponse> createLifeArea(Authentication a,@Valid @RequestBody CreateLifeAreaRequest r){return ResponseEntity.ok(lifeAreaService.create(a.getName(),r));}
    @PutMapping("/{id}") public ResponseEntity<LifeAreaResponse> updateLifeArea(Authentication a,@PathVariable Long id,@Valid @RequestBody UpdateLifeAreaRequest r){return ResponseEntity.ok(lifeAreaService.update(a.getName(),id,r));}
    @DeleteMapping("/{id}") public ResponseEntity<Void> deleteLifeArea(Authentication a,@PathVariable Long id){lifeAreaService.delete(a.getName(),id);return ResponseEntity.noContent().build();}
    @PutMapping("/{id}/move") public ResponseEntity<LifeAreaResponse> moveLifeArea(Authentication a,@PathVariable Long id,@Valid @RequestBody MoveLifeAreaRequest r){return ResponseEntity.ok(lifeAreaService.move(a.getName(),id,r));}
}
