package ru.wolf.api.today;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import ru.wolf.api.today.dto.*;
@RestController @RequestMapping("/api/v1/today") @RequiredArgsConstructor
public class TodayBacklogController {
    private final TodayBacklogService service;
@GetMapping("/backlog") public ResponseEntity<BacklogResponse> backlog(Authentication a,@RequestParam String date){return ResponseEntity.ok(service.backlog(a.getName(),date));
    }
}
