/*
 * WOLF — Wolf's Own Life Framework
 * Copyright (C) 2025 Pavel Obukhov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.loadcharts;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.gantt.GanttForecastService;
import ru.wolf.api.gantt.WeekPlan;
import ru.wolf.api.gantt.WeekPlanRepository;
import ru.wolf.api.loadcharts.dto.*;
import ru.wolf.api.loadcurve.LoadCurveEntry;
import ru.wolf.api.loadcurve.LoadCurveEntryRepository;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.routine.Routine;
import ru.wolf.api.routine.RoutineRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Единый источник чисел для четырёх диаграмм нагрузки (release 0.8).
 * Вся формула живёт здесь — фронт не дублирует её.
 */
@Service
@RequiredArgsConstructor
public class LoadChartsService {

    /** Среднее число недель в месяце — для перевода кривой (час/нед) в часы месяца. */
    private static final BigDecimal WEEKS_PER_MONTH = BigDecimal.valueOf(4.33);
    private static final int DEFAULT_HORIZON_MONTHS = 18;

    private final ProjectRepository projectRepository;
    private final DeloProjectRepository deloProjectRepository;
    private final LoadCurveEntryRepository loadCurveRepository;
    private final WeekPlanRepository weekPlanRepository;
    private final RoutineRepository routineRepository;
    private final UserRepository userRepository;
    private final GanttForecastService ganttForecastService;

    @Transactional(readOnly = true)
    public LoadChartsResponse loadCharts(String username, Integer horizonMonths, String lifeAreaIds) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        int horizon = horizonMonths != null && horizonMonths > 0 ? horizonMonths : DEFAULT_HORIZON_MONTHS;
        Set<Long> areaFilter = parseLifeAreaIds(lifeAreaIds);

        LocalDate startMonday = LocalDate.now(user.getTimezone() != null ? java.time.ZoneId.of(user.getTimezone()) : java.time.ZoneId.systemDefault())
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        BigDecimal hoursPerDelo = user.getHoursPerDelo() != null ? user.getHoursPerDelo() : BigDecimal.valueOf(1.5);
        BigDecimal weeklyLimit = user.getAvailableWeeklyHours() != null ? user.getAvailableWeeklyHours() : BigDecimal.ZERO;
        boolean allProjects = "ALL_PROJECTS".equalsIgnoreCase(user.getHourAccountingMode());

        List<Project> projects = projectRepository.findInProgressByUserOrderByTitleAsc(user).stream()
                .filter(p -> areaFilter.isEmpty() || (p.getLifeArea() != null && areaFilter.contains(p.getLifeArea().getId())))
                .toList();

        Map<Long, BigDecimal> accumulatedFact = ganttForecastService.accumulatedFactByProject(user);

        List<LoadChartsProjectResponse> projectResponses = new ArrayList<>();
        // часы проектов по месяцам для monthlyLoad
        Map<YearMonth, BigDecimal> projectMonthly = new HashMap<>();

        for (Project p : projects) {
            long deloCount = allProjects
                    ? deloProjectRepository.countByProjectId(p.getId())
                    : deloProjectRepository.countPrimaryByProjectId(p.getId());

            BigDecimal effortHours;
            String effortSource;
            if (p.getTotalPlanHours() != null && p.getTotalPlanHours().compareTo(BigDecimal.ZERO) > 0) {
                effortHours = scale(p.getTotalPlanHours());
                effortSource = "PLAN";
            } else {
                effortHours = scale(hoursPerDelo.multiply(BigDecimal.valueOf(deloCount)));
                effortSource = "ESTIMATED";
            }

            BigDecimal fact = scale(accumulatedFact.getOrDefault(p.getId(), BigDecimal.ZERO));
            BigDecimal remaining = effortHours.subtract(fact).max(BigDecimal.ZERO);

            BigDecimal weeklyPlanHours = weeklyPlanHours(user, p, startMonday);

            List<LoadCurveEntry> curve = loadCurveRepository.findByProjectIdOrderByWeekStart(p.getId());

            LocalDate forecastByRate = forecastByRate(remaining, weeklyPlanHours, startMonday);
            LocalDate forecastByCurve = forecastByCurve(remaining, curve, startMonday, horizon);

            projectResponses.add(LoadChartsProjectResponse.build(
                    p, deloCount, effortHours, effortSource, fact, remaining,
                    weeklyPlanHours, curve, forecastByCurve, forecastByRate));

            // суммарная загрузка проекта по месяцам
            accumulateProjectMonthly(projectMonthly, curve, weeklyPlanHours, effortSource, startMonday, horizon);
        }

        // Рутины — только в monthlyLoad
        List<Routine> routines = routineRepository.findByUserAndArchivedOrderByTitleAsc(user, false);
        BigDecimal routineMonthlyTotal = BigDecimal.ZERO;
        for (Routine r : routines) {
            BigDecimal m = scale(r.getWeeklyHours() != null ? r.getWeeklyHours() : BigDecimal.ZERO)
                    .multiply(WEEKS_PER_MONTH);
            routineMonthlyTotal = routineMonthlyTotal.add(m);
        }

        List<LoadChartsRoutineResponse> routineResponses = routines.stream()
                .map(LoadChartsRoutineResponse::from).toList();

        List<MonthlyLoadResponse> monthlyLoad = new ArrayList<>();
        for (int i = 0; i < horizon; i++) {
            YearMonth ym = YearMonth.from(startMonday).plusMonths(i);
            BigDecimal hours = scale(projectMonthly.getOrDefault(ym, BigDecimal.ZERO).add(routineMonthlyTotal));
            monthlyLoad.add(MonthlyLoadResponse.of(ym.toString(), hours, weeklyLimit));
        }

        return new LoadChartsResponse(
                weeklyLimit,
                hoursPerDelo,
                user.getHourAccountingMode(),
                horizon,
                startMonday,
                projectResponses,
                routineResponses,
                monthlyLoad
        );
    }

    private void accumulateProjectMonthly(
            Map<YearMonth, BigDecimal> projectMonthly,
            List<LoadCurveEntry> curve,
            BigDecimal weeklyPlanHours,
            String effortSource,
            LocalDate startMonday,
            int horizon) {
        if (curve.isEmpty()) {
            // без кривой — ставка из WeekPlan (rate) на каждый месяц горизонта
            BigDecimal monthly = scale(weeklyPlanHours).multiply(WEEKS_PER_MONTH);
            for (int i = 0; i < horizon; i++) {
                YearMonth ym = YearMonth.from(startMonday).plusMonths(i);
                projectMonthly.merge(ym, monthly, BigDecimal::add);
            }
            return;
        }
        // по кривой: hoursAt(month) × 4.33
        for (int i = 0; i < horizon; i++) {
            YearMonth ym = YearMonth.from(startMonday).plusMonths(i);
            BigDecimal hoursPerWeek = hoursAt(curve, startMonday, ym);
            BigDecimal monthly = scale(hoursPerWeek).multiply(WEEKS_PER_MONTH);
            projectMonthly.merge(ym, monthly, BigDecimal::add);
        }
    }

    /** Часы в неделю по кривой для месяца — ступенчато по weekStart. */
    private BigDecimal hoursAt(List<LoadCurveEntry> curve, LocalDate startMonday, YearMonth ym) {
        LocalDate monthStartMonday = ym.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (monthStartMonday.isBefore(startMonday)) monthStartMonday = startMonday;
        BigDecimal rate = BigDecimal.ZERO;
        LocalDate active = null;
        for (LoadCurveEntry e : curve) {
            if (!e.getWeekStart().isAfter(monthStartMonday)) {
                if (active == null || e.getWeekStart().isAfter(active)) {
                    active = e.getWeekStart();
                    rate = e.getHours() != null ? e.getHours() : BigDecimal.ZERO;
                }
            }
        }
        return rate;
    }

    private BigDecimal weeklyPlanHours(User user, Project project, LocalDate startMonday) {
        int isoYear = startMonday.get(java.time.temporal.WeekFields.ISO.weekBasedYear());
        int isoWeek = startMonday.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
        WeekPlan plan = weekPlanRepository.findByUserAndProjectIdAndIsoYearAndIsoWeek(user, project.getId(), isoYear, isoWeek).orElse(null);
        return plan != null && plan.getPlanHours() != null ? plan.getPlanHours() : BigDecimal.ZERO;
    }

    private LocalDate forecastByRate(BigDecimal remaining, BigDecimal weeklyPlanHours, LocalDate startMonday) {
        // пустой проект (объём 0) — финиша нет
        if (remaining.signum() == 0) return null;
        if (weeklyPlanHours == null || weeklyPlanHours.signum() == 0) return null;
        double weeks = remaining.doubleValue() / weeklyPlanHours.doubleValue();
        long ceilWeeks = (long) Math.ceil(weeks);
        return startMonday.plusWeeks(ceilWeeks);
    }

    private LocalDate forecastByCurve(BigDecimal remaining, List<LoadCurveEntry> curve, LocalDate startMonday, int horizon) {
        if (curve.isEmpty()) return null;
        // пустой проект (объём 0, нет кривой) — финиша нет, как и у rate
        if (remaining.signum() == 0) return null;
        BigDecimal accumulated = BigDecimal.ZERO;
        for (int i = 0; i < horizon; i++) {
            YearMonth ym = YearMonth.from(startMonday).plusMonths(i);
            BigDecimal hoursPerWeek = hoursAt(curve, startMonday, ym);
            BigDecimal monthHours = scale(hoursPerWeek).multiply(WEEKS_PER_MONTH);
            accumulated = accumulated.add(monthHours);
            if (accumulated.compareTo(remaining) >= 0) {
                // финиш — первый понедельник этого месяца
                return ym.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            }
        }
        return null;
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private Set<Long> parseLifeAreaIds(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        Set<Long> ids = new HashSet<>();
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) continue;
            try {
                ids.add(Long.parseLong(t));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Некорректный lifeAreaIds: " + raw);
            }
        }
        return ids;
    }
}
