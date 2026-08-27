package ru.wolf.api.checklist;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.checklist.dto.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/checklist")
@RequiredArgsConstructor
public class ChecklistController {
    private final ChecklistService service;

    @GetMapping
    public List<Response> list(Authentication authentication, @RequestParam LocalDate date) { return service.list(authentication.getName(), date); }

    @PostMapping
    public ResponseEntity<Response> create(Authentication authentication, @Valid @RequestBody Request request) { return ResponseEntity.status(201).body(service.create(authentication.getName(), request)); }

    @PatchMapping("/{id}")
    public Response update(Authentication authentication, @PathVariable Long id, @RequestBody Update request) { return service.update(authentication.getName(), id, request); }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) { service.delete(authentication.getName(), id); return ResponseEntity.noContent().build(); }

    @PostMapping("/{id}/carry-over")
    public Response carry(Authentication authentication, @PathVariable Long id, @RequestBody Carry request) { return service.carry(authentication.getName(), id, request); }
}
