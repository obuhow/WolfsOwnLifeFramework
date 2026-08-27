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
package ru.wolf.api.today;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import ru.wolf.api.today.dto.*;
import ru.wolf.api.backlog.*;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.timeentry.*;
import ru.wolf.api.user.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;


@Service @RequiredArgsConstructor
public class TodayBacklogService {
    private final UserRepository users;
    private final BacklogItemRepository backlog;
    private final TimeEntryRepository entries;
    private final DeloProjectRepository links;
    @Transactional(readOnly = true)
    public BacklogResponse backlog(String username, String date) {
        User user = current(username); LocalDate day = LocalDate.parse(date); WeekFields wf = WeekFields.ISO;
        String weekId = "%d-W%02d".formatted(day.get(wf.weekBasedYear()), day.get(wf.weekOfWeekBasedYear()));
        LocalDate monday = day.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<BacklogItem> items = backlog.findPeriod(user, BacklogItem.Scope.WEEK, weekId);
        List<TimeEntry> facts = entries.findByUserIdAndStatusOverlapping(user.getId(), TimeEntry.Status.DONE, monday.atStartOfDay(), monday.plusDays(7).atStartOfDay());
        Map<Long, BigDecimal> factByDelo = facts.stream().filter(e -> e.getDelo() != null).collect(Collectors.groupingBy(e -> e.getDelo().getId(), Collectors.reducing(BigDecimal.ZERO, e -> BigDecimal.valueOf(Duration.between(e.getStartAt(), e.getEndAt()).toMinutes()).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP), BigDecimal::add)));
        BigDecimal totalFact = BigDecimal.ZERO.setScale(2), totalPlan = BigDecimal.ZERO.setScale(2); List<ItemResponse> result = new ArrayList<>();
        for (BacklogItem item : items) { BigDecimal fact = factByDelo.getOrDefault(item.getDelo().getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP); if (item.getPlannedHours() != null) totalPlan = totalPlan.add(item.getPlannedHours()); totalFact = totalFact.add(fact); result.add(new ItemResponse(item.getDelo().getId(), item.getDelo().getTitle(), item.getPlannedHours(), fact, links.findByDeloId(item.getDelo().getId()).stream().findFirst().map(l -> l.getProject().getTitle()).orElse(null))); }
        return new BacklogResponse(weekId, result, totalPlan, totalFact);
    }
    private User current(String username) { return users.findByUsername(username).orElseThrow(); }
}
