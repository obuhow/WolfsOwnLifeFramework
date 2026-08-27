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
 * along with this program. If not see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.backlog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.backlog.dto.DeloResponse;
import ru.wolf.api.backlog.dto.WeekBacklogResponse;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WeekBacklogService {

    private final BacklogItemRepository items;
    private final DeloRepository delos;
    private final UserRepository users;

    public WeekBacklogResponse current(String username) {
        LocalDate today = LocalDate.now(ZoneId.of(currentUser(username).getTimezone()));
        return weekResponse(username, today.get(WeekFields.ISO.weekBasedYear()), today.get(WeekFields.ISO.weekOfWeekBasedYear()));
    }

    @Transactional(readOnly = true)
    public WeekBacklogResponse weekResponse(String username, int year, int week) {
        User user = currentUser(username);
        String period = "%d-W%02d".formatted(year, week);
        List<DeloResponse> result = items.findPeriod(user, BacklogItem.Scope.WEEK, period).stream().map(this::delo).toList();
        LocalDate monday = LocalDate.of(year, 1, 4).with(WeekFields.ISO.weekOfWeekBasedYear(), week).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new WeekBacklogResponse(year, week, monday.toString(), monday.plusDays(7).toString(), result);
    }

    @Transactional
    public WeekBacklogResponse add(String username, int year, int week, Long deloId) {
        User user = currentUser(username);
        Delo delo = delos.findByUserAndId(user, deloId).orElseThrow();
        String period = "%d-W%02d".formatted(year, week);
        if (items.findByUserAndDeloIdAndScopeAndPeriodId(user, deloId, BacklogItem.Scope.WEEK, period).isEmpty()) {
            items.save(BacklogItem.builder().user(user).delo(delo).scope(BacklogItem.Scope.WEEK).periodId(period).position(0).build());
        }
        return weekResponse(username, year, week);
    }

    @Transactional
    public WeekBacklogResponse remove(String username, int year, int week, Long deloId) {
        BacklogItem item = items.findByUserAndDeloIdAndScopeAndPeriodId(currentUser(username), deloId, BacklogItem.Scope.WEEK, "%d-W%02d".formatted(year, week)).orElseThrow();
        items.delete(item);
        return weekResponse(username, year, week);
    }

    private User currentUser(String username) {
        return users.findByUsername(username).orElseThrow();
    }

    private DeloResponse delo(BacklogItem item) {
        return new DeloResponse(item.getDelo().getId(), item.getDelo().getTitle(), item.getDelo().getExecutionMode(), item.getPlannedHours());
    }
}
