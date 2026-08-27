package ru.wolf.api.checklist;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.checklist.dto.*;
import java.time.LocalDate;
import java.util.List;
@RestController @RequestMapping("/api/v1/checklist") @RequiredArgsConstructor
public class ChecklistController {
 private final ChecklistService service;
 @GetMapping public List<Response> list(Authentication a,@RequestParam LocalDate date){return service.list(a,date);}
 @PostMapping public ResponseEntity<Response> create(Authentication a,@RequestBody Request r){return service.create(a,r);}
 @PatchMapping("/{id}") public Response update(Authentication a,@PathVariable Long id,@RequestBody Update r){return service.update(a,id,r);}
 @DeleteMapping("/{id}") public ResponseEntity<Void> delete(Authentication a,@PathVariable Long id){return service.delete(a,id);}
 @PostMapping("/{id}/carry-over") public Response carry(Authentication a,@PathVariable Long id,@RequestBody Carry r){return service.carry(a,id,r);}
}
