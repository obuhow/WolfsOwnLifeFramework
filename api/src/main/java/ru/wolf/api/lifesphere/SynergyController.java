package ru.wolf.api.lifesphere;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.lifesphere.dto.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/synergies")
@RequiredArgsConstructor
public class SynergyController {
 private final SynergyService synergyService;
 @PostMapping public ResponseEntity<SynergyResponse> createSynergy(Authentication a,@Valid @RequestBody CreateSynergyRequest r){return ResponseEntity.ok(synergyService.create(a.getName(),r));}
 @GetMapping public ResponseEntity<List<SynergyResponse>> getSynergies(Authentication a,@RequestParam(required=false) Long projectId,@RequestParam(required=false) Long ideaId,@RequestParam(required=false) Long routineId){return ResponseEntity.ok(synergyService.list(a.getName(),projectId,ideaId,routineId));}
 @DeleteMapping("/{id}") public ResponseEntity<Void> deleteSynergy(Authentication a,@PathVariable Long id){synergyService.delete(a.getName(),id);return ResponseEntity.noContent().build();}
 @PutMapping("/{id}") public ResponseEntity<SynergyResponse> updateSynergy(Authentication a,@PathVariable Long id,@Valid @RequestBody UpdateSynergyRequest r){return ResponseEntity.ok(synergyService.update(a.getName(),id,r));}
}
