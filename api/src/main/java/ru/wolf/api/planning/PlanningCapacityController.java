package ru.wolf.api.planning;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.wolf.api.routine.Routine;
import ru.wolf.api.routine.RoutineRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/planning")
@RequiredArgsConstructor
public class PlanningCapacityController {

    private final RoutineRepository routineRepository;
    private final UserRepository userRepository;

    @GetMapping("/capacity")
    @Transactional(readOnly = true)
    public ResponseEntity<List<CapacityResponse>> capacity(
            Authentication authentication,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("Дата окончания не может быть раньше даты начала");
        }
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
        LocalDate monday = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastMonday = to.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        BigDecimal routineHours = routineRepository.findByUserAndArchivedOrderByTitleAsc(user, false).stream()
                .map(Routine::getWeeklyHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal available = user.getAvailableWeeklyHours().setScale(2, RoundingMode.HALF_UP);

        List<CapacityResponse> response = new ArrayList<>();
        for (LocalDate week = monday; !week.isAfter(lastMonday); week = week.plusWeeks(1)) {
            BigDecimal planned = routineHours;
            response.add(new CapacityResponse(
                    "%d-W%02d".formatted(week.get(java.time.temporal.WeekFields.ISO.weekBasedYear()),
                            week.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())),
                    planned,
                    routineHours,
                    available,
                    available.subtract(planned).setScale(2, RoundingMode.HALF_UP)
            ));
        }
        return ResponseEntity.ok(response);
    }

    @Data
    @AllArgsConstructor
    public static class CapacityResponse {
        private String weekId;
        private BigDecimal plannedHours;
        private BigDecimal routineHours;
        private BigDecimal availableHours;
        private BigDecimal delta;
    }
}
