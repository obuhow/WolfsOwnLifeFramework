package ru.wolf.api.planning;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service; import org.springframework.http.ResponseEntity; import ru.wolf.api.routine.*; import ru.wolf.api.user.*; import ru.wolf.api.gantt.*; import ru.wolf.api.loadcurve.*; import ru.wolf.api.project.*; import ru.wolf.api.planning.dto.CapacityResponse; import java.util.*; import java.math.*; import java.time.*; import java.time.temporal.*;
@Service @RequiredArgsConstructor public class PlanningCapacityService {


    private final RoutineRepository routineRepository;
    private final UserRepository userRepository;
    private final WeekPlanRepository weekPlanRepository;
    private final LoadCurveEntryRepository loadCurveRepository;

    public ResponseEntity<List<CapacityResponse>> capacity(
            String username,
            LocalDate from,
            LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("Дата окончания не может быть раньше даты начала");
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        LocalDate monday = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastMonday = to.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<Routine> routines = routineRepository.findByUserAndArchivedOrderByTitleAsc(user, false);
        List<LoadCurveEntry> curveEntries = loadCurveRepository.findRange(user, monday, lastMonday);
        BigDecimal routineHours = routines.stream().map(Routine::getWeeklyHours).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal available = user.getAvailableWeeklyHours().setScale(2, RoundingMode.HALF_UP);
        List<WeekPlan> projectPlans = weekPlanRepository.findInWeekRange(user,
                monday.get(java.time.temporal.WeekFields.ISO.weekBasedYear()), monday.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear()),
                lastMonday.get(java.time.temporal.WeekFields.ISO.weekBasedYear()), lastMonday.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear()));

        List<CapacityResponse> response = new ArrayList<>();
        for (LocalDate week = monday; !week.isAfter(lastMonday); week = week.plusWeeks(1)) {
            int isoYear = week.get(java.time.temporal.WeekFields.ISO.weekBasedYear());
            int isoWeek = week.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
            final LocalDate currentWeek = week;
            String weekId = "%d-W%02d".formatted(isoYear, isoWeek);
            BigDecimal projectHours = projectPlans.stream()
                    .filter(plan -> plan.getIsoYear() == isoYear
                            && plan.getIsoWeek() == isoWeek
                            && plan.getProject().getStatus() == Project.Status.IN_PROGRESS)
                    .map(WeekPlan::getPlanHours)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal curveRoutineHours = routines.stream().mapToDouble(r -> curveEntries.stream().filter(e -> e.getRoutine() != null && e.getRoutine().getId().equals(r.getId()) && e.getWeekStart().equals(currentWeek)).map(LoadCurveEntry::getHours).findFirst().orElse(r.getWeeklyHours()).doubleValue()).mapToObj(BigDecimal::valueOf).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal curveProjectHours = projectPlans.stream().filter(plan -> plan.getIsoYear() == isoYear && plan.getIsoWeek() == isoWeek && plan.getProject().getStatus() == Project.Status.IN_PROGRESS).map(plan -> curveEntries.stream().filter(e -> e.getProject() != null && e.getProject().getId().equals(plan.getProject().getId()) && e.getWeekStart().equals(currentWeek)).map(LoadCurveEntry::getHours).findFirst().orElse(plan.getPlanHours())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal standaloneCurveProjectHours = curveEntries.stream().filter(e -> e.getProject() != null && e.getProject().getStatus() == Project.Status.IN_PROGRESS && e.getWeekStart().equals(currentWeek) && projectPlans.stream().noneMatch(plan -> plan.getProject().getId().equals(e.getProject().getId()) && plan.getIsoYear() == isoYear && plan.getIsoWeek() == isoWeek)).map(LoadCurveEntry::getHours).reduce(BigDecimal.ZERO, BigDecimal::add);
            curveProjectHours = curveProjectHours.add(standaloneCurveProjectHours);
            BigDecimal planned = curveRoutineHours.add(curveProjectHours).setScale(2, RoundingMode.HALF_UP);
            response.add(new CapacityResponse(
                    weekId,
                    planned,
                    curveRoutineHours.setScale(2, RoundingMode.HALF_UP),
                    available,
                    available.subtract(planned).setScale(2, RoundingMode.HALF_UP)
            ));
        }
        return ResponseEntity.ok(response);
    }

}
