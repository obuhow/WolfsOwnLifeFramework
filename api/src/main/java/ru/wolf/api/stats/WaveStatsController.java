package ru.wolf.api.stats;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.stats.dto.*;
import java.util.List;
@RestController @RequestMapping("/api/v1/stats") @RequiredArgsConstructor
public class WaveStatsController {
 private final WaveStatsService service;
 @GetMapping("/wave") public ResponseEntity<List<GoalWave>> wave(Authentication a,@RequestParam(defaultValue="12") int weeks){return service.wave(a,weeks);}
}
