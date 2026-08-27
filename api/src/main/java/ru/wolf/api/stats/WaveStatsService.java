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
package ru.wolf.api.stats;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import ru.wolf.api.stats.dto.*;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.goal.Goal;
import ru.wolf.api.goal.GoalProjectRepository;
import ru.wolf.api.goal.GoalRepository;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.*;

@Service @RequiredArgsConstructor
public class WaveStatsService {
    private final UserRepository users;
    private final GoalRepository goals;
    private final GoalProjectRepository goalProjects;
    private final DeloProjectRepository deloProjects;
    private final TimeEntryRepository entries;
    @Transactional(readOnly = true)
    public List<GoalWave> wave(String username, int weeks) {
        if (weeks != 4 && weeks != 12 && weeks != 26) throw new IllegalArgumentException("weeks должен быть 4, 12 или 26");
        User user = users.findByUsername(username).orElseThrow();
        LocalDate end = LocalDate.now(ZoneId.of(user.getTimezone())).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDate start = end.minusWeeks(weeks);
        List<Week> weekList = new ArrayList<>();
        for (LocalDate d = start; d.isBefore(end); d = d.plusWeeks(1)) {
            weekList.add(new Week("%d-W%02d".formatted(d.get(WeekFields.ISO.weekBasedYear()), d.get(WeekFields.ISO.weekOfWeekBasedYear())), d, d.plusWeeks(1)));
        }
        List<TimeEntry> facts = entries.findOverlapping(user.getId(), start.atStartOfDay(), end.atStartOfDay());
        Map<Long, Set<Long>> goalProjectsByGoal = new HashMap<>();
        Map<Long, Set<Long>> projectDelos = new HashMap<>();
        for (Goal goal : goals.findByUserAndArchivedOrderByPriorityAsc(user, false)) {
            Set<Long> pids = new HashSet<>();
            goalProjects.findByGoalId(goal.getId()).forEach(link -> pids.add(link.getProject().getId()));
            goalProjectsByGoal.put(goal.getId(), pids);
        }
        for (var link : facts.stream().filter(e -> e.getDelo() != null).flatMap(e -> deloProjects.findByDeloId(e.getDelo().getId()).stream()).toList())
            projectDelos.computeIfAbsent(link.getProject().getId(), ignored -> new HashSet<>()).add(link.getDelo().getId());
        List<GoalWave> response = new ArrayList<>();
        for (Goal goal : goals.findByUserAndArchivedOrderByPriorityAsc(user, false)) {
            List<BigDecimal> values = new ArrayList<>();
            for (Week week : weekList) {
                BigDecimal total = BigDecimal.ZERO;
                for (TimeEntry entry : facts) {
                    if (entry.getStatus() != TimeEntry.Status.DONE || entry.getDelo() == null) continue;
                    boolean linked = goalProjectsByGoal.getOrDefault(goal.getId(), Set.of()).stream().anyMatch(pid -> projectDelos.getOrDefault(pid, Set.of()).contains(entry.getDelo().getId()));
                    if (!linked) continue;
                    LocalDateTime from = entry.getStartAt().isAfter(week.start.atStartOfDay()) ? entry.getStartAt() : week.start.atStartOfDay();
                    LocalDateTime to = entry.getEndAt().isBefore(week.end.atStartOfDay()) ? entry.getEndAt() : week.end.atStartOfDay();
                    if (to.isAfter(from)) total = total.add(BigDecimal.valueOf(Duration.between(from, to).toMinutes()).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
                }
                values.add(total.setScale(2, RoundingMode.HALF_UP));
            }
            List<Double> sorted = values.stream().map(BigDecimal::doubleValue).sorted().toList();
            double avg = values.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
            double median = sorted.isEmpty() ? 0 : sorted.size() % 2 == 1 ? sorted.get(sorted.size()/2) : (sorted.get(sorted.size()/2-1)+sorted.get(sorted.size()/2))/2;
            response.add(new GoalWave(goal.getId(), goal.getTitle(), weekList.stream().map(w -> new WeekFact(w.id, values.get(weekList.indexOf(w)))).toList(), avg, median, sorted.stream().mapToDouble(x -> x).max().orElse(0), sorted.stream().mapToDouble(x -> x).min().orElse(0)));
        }
        return response;
    }
    private record Week(String id, LocalDate start, LocalDate end) {}
}
