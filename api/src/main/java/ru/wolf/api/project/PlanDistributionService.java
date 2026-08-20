package ru.wolf.api.project;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.gantt.WeekPlan;
import ru.wolf.api.gantt.WeekPlanRepository;
import ru.wolf.api.user.User;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlanDistributionService {

    private final WeekPlanRepository weekPlanRepository;

    @Transactional
    public DistributionResult apply(User user, Project project, Project.PlanDistribution mode) {
        if (mode == null || mode == Project.PlanDistribution.NONE) {
            throw new IllegalArgumentException("Выберите режим распределения");
        }
        if (project.getStartDate() == null || project.getEndDate() == null || project.getTotalPlanHours() == null) {
            throw new IllegalArgumentException("Для распределения нужны даты и плановые часы проекта");
        }
        if (project.getEndDate().isBefore(project.getStartDate())) {
            throw new IllegalArgumentException("Дата окончания не может быть раньше даты начала");
        }

        List<LocalDate> days = eligibleDays(project.getStartDate(), project.getEndDate(), mode);
        if (days.isEmpty()) {
            throw new IllegalArgumentException("В диапазоне проекта нет подходящих дней");
        }
        long totalMinutes = project.getTotalPlanHours().multiply(BigDecimal.valueOf(60)).longValueExact();
        long baseMinutes = (totalMinutes / days.size() / 15) * 15;
        Map<String, BigDecimal> byWeek = new LinkedHashMap<>();
        for (int i = 0; i < days.size(); i++) {
            long minutes = i == days.size() - 1 ? totalMinutes - baseMinutes * (days.size() - 1) : baseMinutes;
            LocalDate day = days.get(i);
            WeekFields iso = WeekFields.ISO;
            String weekId = "%d-W%02d".formatted(day.get(iso.weekBasedYear()), day.get(iso.weekOfWeekBasedYear()));
            byWeek.merge(weekId, BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP), BigDecimal::add);
        }

        List<WeekPlan> existing = weekPlanRepository.findInWeekRange(user, days.get(0).get(WeekFields.ISO.weekBasedYear()), days.get(0).get(WeekFields.ISO.weekOfWeekBasedYear()),
                days.get(days.size() - 1).get(WeekFields.ISO.weekBasedYear()), days.get(days.size() - 1).get(WeekFields.ISO.weekOfWeekBasedYear())).stream()
                .filter(plan -> plan.getProject().getId().equals(project.getId())).toList();
        List<String> overwritten = existing.stream().map(plan -> "%d-W%02d".formatted(plan.getIsoYear(), plan.getIsoWeek())).toList();
        existing.forEach(weekPlanRepository::delete);
        List<WeekPlan> saved = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : byWeek.entrySet()) {
            String[] parts = entry.getKey().split("-W");
            saved.add(weekPlanRepository.save(WeekPlan.builder().user(user).project(project)
                    .isoYear(Integer.parseInt(parts[0])).isoWeek(Integer.parseInt(parts[1]))
                    .planHours(entry.getValue().setScale(2, RoundingMode.HALF_UP)).build()));
        }
        return new DistributionResult(saved.stream().map(p -> new WeekHours("%d-W%02d".formatted(p.getIsoYear(), p.getIsoWeek()), p.getPlanHours())).toList(), overwritten);
    }

    private List<LocalDate> eligibleDays(LocalDate start, LocalDate end, Project.PlanDistribution mode) {
        List<LocalDate> result = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            if (mode == Project.PlanDistribution.EVEN_ALL_DAYS || (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY)) result.add(day);
        }
        return result;
    }

    public record DistributionResult(List<WeekHours> weeks, List<String> overwritten) {}
    public record WeekHours(String weekId, BigDecimal hours) {}
}
/* end */

