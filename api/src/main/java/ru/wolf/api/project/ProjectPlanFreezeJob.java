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