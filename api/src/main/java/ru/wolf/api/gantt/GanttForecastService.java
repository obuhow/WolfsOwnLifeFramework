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
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.gantt;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.delo.DeloProject;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectDependency;
import ru.wolf.api.project.ProjectDependencyRepository;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.timeentry.DayBounds;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.gantt.dto.ForecastResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GanttForecastService {

    private static final BigDecimal MIN_WEEKLY_AVERAGE = BigDecimal.valueOf(0.5);
    private static final BigDecimal FOUR = BigDecimal.valueOf(4);

    private final ProjectRepository projectRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final DeloProjectRepository deloProjectRepository;
    private final ProjectDependencyRepository dependencyRepository;

    @Transactional(readOnly = true)
    public List<ForecastResponse> forecast(User user) {
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalTime dayEnd = user.getDayEnd() != null ? user.getDayEnd() : LocalTime.of(2, 0);
        LocalDate currentMonday = LocalDate.now(zone)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate historyStart = currentMonday.minusWeeks(4);
        LocalDateTime queryFrom = DayBounds.forDay(historyStart, dayEnd).start();
        // The current, still-open ISO week is not part of the four-week average.
        LocalDateTime queryTo = DayBounds.forDay(currentMonday, dayEnd).start();

        List<TimeEntry> recentEntries = timeEntryRepository.findByUserIdAndStatusOverlapping(
                user.getId(), TimeEntry.Status.DONE, queryFrom, queryTo);
        LocalDateTime accumulatedTo = LocalDateTime.now(zone);
        List<TimeEntry> allEntries = timeEntryRepository.findByUserIdAndStatusOverlapping(
                user.getId(), TimeEntry.Status.DONE,
                LocalDateTime.of(1970, 1, 1, 0, 0), accumulatedTo);
        Map<Long, List<DeloProject>> linksByDelo = loadDeloLinks(recentEntries, allEntries);
        Map<Long, BigDecimal> recentFact = aggregateProjectFact(
                user, recentEntries, linksByDelo, queryFrom, queryTo);
        Map<Long, BigDecimal> accumulatedFact = aggregateProjectFact(
                user, allEntries, linksByDelo, LocalDateTime.of(1970, 1, 1, 0, 0), accumulatedTo);

        Map<Long, ForecastValue> values = new HashMap<>();
        for (Project project : projectRepository.findByUserOrderByTitleAsc(user)) {
            BigDecimal plan = project.getTotalPlanHours() == null
                    ? null
                    : scale(project.getTotalPlanHours());
            BigDecimal accumulated = scale(accumulatedFact.getOrDefault(project.getId(), BigDecimal.ZERO));
            BigDecimal remaining = plan == null
                    ? null
                    : plan.subtract(accumulated).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal weeklyAvg = scale(recentFact.getOrDefault(project.getId(), BigDecimal.ZERO))
                    .divide(FOUR, 2, RoundingMode.HALF_UP);
            LocalDate forecastEnd = null;
            if (remaining != null && remaining.signum() == 0) {
                forecastEnd = currentMonday;
            } else if (remaining != null && weeklyAvg.compareTo(MIN_WEEKLY_AVERAGE) >= 0) {
                long forecastWeeks = (long) Math.ceil(remaining.doubleValue() / weeklyAvg.doubleValue());
                forecastEnd = currentMonday.plusWeeks(forecastWeeks);
            }
            values.put(project.getId(), new ForecastValue(
                    project.getId(), project.getEndDate(), forecastEnd, weeklyAvg, remaining));
        }

        Map<Long, List<ProjectDependency>> dependenciesByBlocked = dependencyRepository.findAllForUser(user)
                .stream()
                .collect(Collectors.groupingBy(d -> d.getBlocked().getId()));
        for (Long projectId : values.keySet()) {
            resolveBlockedForecast(values, dependenciesByBlocked, projectId, currentMonday, new HashSet<>());
        }

        return values.values().stream()
                .sorted((a, b) -> Long.compare(a.projectId(), b.projectId()))
                .map(ForecastValue::toResponse)
                .toList();
    }

    private void resolveBlockedForecast(
            Map<Long, ForecastValue> values,
            Map<Long, List<ProjectDependency>> dependenciesByBlocked,
            Long projectId,
            LocalDate currentMonday,
            Set<Long> visiting
    ) {
        ForecastValue own = values.get(projectId);
        if (own == null || own.forecastEnd() == null || !visiting.add(projectId)) {
            return;
        }
        LocalDate latestBlockerEnd = null;
        for (ProjectDependency dependency : dependenciesByBlocked.getOrDefault(projectId, List.of())) {
            Long blockerId = dependency.getBlocker().getId();
            resolveBlockedForecast(values, dependenciesByBlocked, blockerId, currentMonday, visiting);
            LocalDate blockerEnd = values.get(blockerId) != null ? values.get(blockerId).forecastEnd() : null;
            if (blockerEnd != null && (latestBlockerEnd == null || blockerEnd.isAfter(latestBlockerEnd))) {
                latestBlockerEnd = blockerEnd;
            }
        }
        if (latestBlockerEnd != null && latestBlockerEnd.isAfter(own.forecastEnd())) {
            long ownDurationWeeks = Math.max(0, Duration.between(currentMonday.atStartOfDay(),
                    own.forecastEnd().atStartOfDay()).toDays() / 7);
            own = own.withForecastEnd(latestBlockerEnd.plusWeeks(ownDurationWeeks));
            values.put(projectId, own);
        }
        visiting.remove(projectId);
    }

    /**
     * Накопленный факт от эпохи до «сейчас» по каждому проекту пользователя,
     * с соблюдением режима учёта часов ({@code hourAccountingMode}).
     * Переиспользуется release 0.8 (диаграммы нагрузки), чтобы вторая формула
     * подсчёта факта не появилась.
     */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> accumulatedFactByProject(User user) {
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDateTime accumulatedTo = LocalDateTime.now(zone);
        LocalDateTime epoch = LocalDateTime.of(1970, 1, 1, 0, 0);
        List<TimeEntry> allEntries = timeEntryRepository.findByUserIdAndStatusOverlapping(
                user.getId(), TimeEntry.Status.DONE, epoch, accumulatedTo);
        Map<Long, List<DeloProject>> linksByDelo = loadDeloLinks(allEntries, allEntries);
        return aggregateProjectFact(user, allEntries, linksByDelo, epoch, accumulatedTo);
    }

    private Map<Long, List<DeloProject>> loadDeloLinks(
            List<TimeEntry> recentEntries,
            List<TimeEntry> allEntries
    ) {
        Set<Long> deloIds = new HashSet<>();
        for (List<TimeEntry> entries : List.of(recentEntries, allEntries)) {
            entries.stream().filter(e -> e.getDelo() != null)
                    .map(e -> e.getDelo().getId()).forEach(deloIds::add);
        }
        Map<Long, List<DeloProject>> links = new HashMap<>();
        for (Long deloId : deloIds) {
            links.put(deloId, deloProjectRepository.findByDeloId(deloId));
        }
        return links;
    }

    public Map<Long, BigDecimal> aggregateProjectFact(
            User user,
            List<TimeEntry> entries,
            Map<Long, List<DeloProject>> linksByDelo,
            LocalDateTime rangeFrom,
            LocalDateTime rangeTo
    ) {
        boolean primaryOnly = !"ALL_PROJECTS".equalsIgnoreCase(user.getHourAccountingMode());
        Map<Long, BigDecimal> result = new HashMap<>();
        for (TimeEntry entry : entries) {
            if (entry.getDelo() == null) continue;
            List<DeloProject> links = linksByDelo.getOrDefault(entry.getDelo().getId(), List.of());
            List<Long> targets;
            if (primaryOnly) {
                Long primary = links.stream().filter(link -> Boolean.TRUE.equals(link.getIsPrimary()))
                        .map(link -> link.getProject().getId()).findFirst().orElse(null);
                if (primary == null) continue;
                targets = List.of(primary);
            } else {
                targets = links.stream().map(link -> link.getProject().getId()).distinct().toList();
            }
            LocalDateTime overlapStart = entry.getStartAt().isAfter(rangeFrom) ? entry.getStartAt() : rangeFrom;
            LocalDateTime overlapEnd = entry.getEndAt().isBefore(rangeTo) ? entry.getEndAt() : rangeTo;
            if (!overlapEnd.isAfter(overlapStart)) continue;
            double hours = Duration.between(overlapStart, overlapEnd).toMinutes() / 60.0;
            BigDecimal amount = BigDecimal.valueOf(hours).setScale(2, RoundingMode.HALF_UP);
            targets.forEach(projectId -> result.merge(projectId, amount, BigDecimal::add));
        }
        return result;
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private record ForecastValue(
            Long projectId,
            LocalDate planEnd,
            LocalDate forecastEnd,
            BigDecimal weeklyAvg,
            BigDecimal remaining
    ) {
        ForecastValue withForecastEnd(LocalDate end) {
            return new ForecastValue(projectId, planEnd, end, weeklyAvg, remaining);
        }

        ForecastResponse toResponse() {
            return new ForecastResponse(projectId, planEnd, forecastEnd, weeklyAvg, remaining);
        }
    }
}
