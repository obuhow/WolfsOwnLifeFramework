package ru.wolf.api.planning;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.wolf.api.planning.dto.CapacityResponse;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/planning")
@RequiredArgsConstructor
public class PlanningCapacityController {
    private final PlanningCapacityService service;

    @GetMapping("/capacity")
    public ResponseEntity<List<CapacityResponse>> capacity(Authentication authentication,
                                                            @RequestParam LocalDate from,
                                                            @RequestParam LocalDate to) {
        return ResponseEntity.ok(service.capacity(authentication.getName(), from, to));
    }
}
