package ru.wolf.api.gantt;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.wolf.api.gantt.dto.ForecastResponse;
import ru.wolf.api.gantt.dto.GanttResponse;
import ru.wolf.api.gantt.dto.UpsertWeekPlanRequest;
import ru.wolf.api.gantt.dto.WeekPlanResponse;

import java.util.List;

@RestController
@RequestMapping("/api/v1/gantt")
@RequiredArgsConstructor
public class GanttController {
    private final GanttService service;

    @GetMapping
    public ResponseEntity<GanttResponse> getGantt(Authentication authentication,
                                                   @RequestParam(required = false) String from,
                                                   @RequestParam(required = false) Integer weeks,
                                                   @RequestParam(required = false) String lifeAreaIds,
                                                   @RequestParam(defaultValue = "false") boolean onlyWithDates) {
        return ResponseEntity.ok(service.getGantt(authentication.getName(), from, weeks,
                lifeAreaIds, onlyWithDates));
    }

    @GetMapping("/forecast")
    public ResponseEntity<List<ForecastResponse>> getForecast(Authentication authentication) {
        return ResponseEntity.ok(service.getForecast(authentication.getName()));
    }

    @PutMapping("/week-plans")
    public ResponseEntity<WeekPlanResponse> upsertWeekPlan(Authentication authentication,
                                                            @Valid @RequestBody UpsertWeekPlanRequest request) {
        return ResponseEntity.ok(service.upsertWeekPlan(authentication.getName(), request));
    }
}
