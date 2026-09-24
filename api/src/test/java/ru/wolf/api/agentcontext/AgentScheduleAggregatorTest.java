package ru.wolf.api.agentcontext;

import org.junit.jupiter.api.Test;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloProject;
import ru.wolf.api.delo.DeloProjectId;
import ru.wolf.api.gantt.WeekPlan;
import ru.wolf.api.lifearea.LifeArea;
import ru.wolf.api.project.Project;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.user.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentScheduleAggregatorTest {

    @Test
    void aggregates_plan_fact_and_trend_without_sending_time_entries_to_context() {
        User user = User.builder().id(7L).hourAccountingMode("PRIMARY_ONLY").build();
        LifeArea work = LifeArea.builder().id(3L).name("Работа").build();
        Project project = Project.builder().id(11L).user(user).lifeArea(work)
                .title("WOLF").status(Project.Status.IN_PROGRESS).build();
        Delo delo = Delo.builder().id(21L).user(user).title("Код").build();
        DeloProject link = DeloProject.builder()
                .id(new DeloProjectId(delo.getId(), project.getId()))
                .delo(delo).project(project).isPrimary(true).build();

        LocalDate firstMonday = LocalDate.of(2026, 1, 5);
        WeekPlan firstPlan = WeekPlan.builder().user(user).project(project)
                .isoYear(2026).isoWeek(3).planHours(new BigDecimal("4.00")).build();
        WeekPlan secondPlan = WeekPlan.builder().user(user).project(project)
                .isoYear(2026).isoWeek(4).planHours(new BigDecimal("2.00")).build();
        TimeEntry firstFact = entry(user, delo, firstMonday.plusWeeks(1).atTime(9, 0), 60, TimeEntry.Status.DONE);
        TimeEntry secondFact = entry(user, delo, firstMonday.plusWeeks(2).atTime(9, 0), 30, TimeEntry.Status.DONE);

        AgentContext.ScheduleDynamics result = AgentScheduleAggregator.aggregate(
                user,
                firstMonday.plusWeeks(1),
                firstMonday.plusWeeks(3),
                List.of(project),
                List.of(firstPlan, secondPlan),
                List.of(firstFact, secondFact),
                Map.of(delo.getId(), List.of(link)),
                List.of());

        assertThat(result.historyAvailable()).isTrue();
        assertThat(result.projectHours()).singleElement().satisfies(hours -> {
            assertThat(hours.label()).isEqualTo("WOLF");
            assertThat(hours.planned()).isEqualByComparingTo("6.00");
            assertThat(hours.fact()).isEqualByComparingTo("1.50");
        });
        assertThat(result.lifeAreaHours()).singleElement().satisfies(hours -> {
            assertThat(hours.label()).isEqualTo("Работа");
            assertThat(hours.fact()).isEqualByComparingTo("1.50");
        });
        assertThat(result.weeks()).extracting(AgentContext.WeekDynamics::factHours)
                .containsExactly(new BigDecimal("1.00"), new BigDecimal("0.50"));
        assertThat(result.trend().direction()).isEqualTo("DOWN");
    }

    @Test
    void marks_no_rows_as_empty_history_instead_of_creating_zero_rows() {
        AgentContext.ScheduleDynamics result = AgentScheduleAggregator.aggregate(
                User.builder().id(7L).build(),
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 1, 19),
                List.of(), List.of(), List.of(), Map.of(), List.of());

        assertThat(result.historyAvailable()).isFalse();
        assertThat(result.weeks()).isEmpty();
        assertThat(result.projectHours()).isEmpty();
        assertThat(result.historyNote()).contains("нет данных");
    }

    private TimeEntry entry(User user, Delo delo, LocalDateTime start, int minutes, TimeEntry.Status status) {
        return TimeEntry.builder().user(user).delo(delo).startAt(start)
                .endAt(start.plusMinutes(minutes)).status(status).build();
    }
}
