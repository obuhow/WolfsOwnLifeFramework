package ru.wolf.api.checklist;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.wolf.api.checklist.dto.Carry;
import ru.wolf.api.checklist.dto.Request;
import ru.wolf.api.checklist.dto.Response;
import ru.wolf.api.checklist.dto.Update;

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
