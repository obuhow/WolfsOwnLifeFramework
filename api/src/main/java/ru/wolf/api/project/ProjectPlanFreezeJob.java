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
package ru.wolf.api.project;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** Keeps the visible Gantt plan baseline stable within a calendar month. */
@Component
@RequiredArgsConstructor
public class ProjectPlanFreezeJob {

    private final ProjectRepository projectRepository;

    @Scheduled(cron = "${wolf.gantt.plan-freeze-cron:0 0 0 1 * *}")
    @Transactional
    public void scheduledFreeze() {
        freezeCurrentMonth();
    }

    @Transactional
    public int freezeCurrentMonth() {
        LocalDate monthStart = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
        var projects = projectRepository.findByPlanFrozenAtBeforeOrPlanFrozenAtIsNull(monthStart);
        projects.forEach(project -> project.setPlanFrozenAt(monthStart));
        projectRepository.saveAll(projects);
        return projects.size();
    }
}