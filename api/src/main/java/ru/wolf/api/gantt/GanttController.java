package ru.wolf.api.gantt;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.gantt.dto.*;
import java.util.List;
@RestController
@RequestMapping("/api/v1/gantt")
@RequiredArgsConstructor
public class GanttController {
 private final GanttService service;
 @GetMapping public ResponseEntity<GanttResponse> getGantt(Authentication a,@RequestParam(required=false) String from,@RequestParam(required=false) Integer weeks,@RequestParam(required=false) String lifeAreaIds,@RequestParam(defaultValue="false") boolean onlyWithDates){ return service.getGantt(a.getName(),from,weeks,lifeAreaIds,onlyWithDates); }
 @GetMapping("/forecast") public ResponseEntity<List<ForecastResponse>> getForecast(Authentication a){ return service.getForecast(a.getName()); }
 @PutMapping("/week-plans") public ResponseEntity<WeekPlanResponse> upsertWeekPlan(Authentication a,@jakarta.validation.Valid @RequestBody UpsertWeekPlanRequest r){ return service.upsertWeekPlan(a.getName(),r); }
}
