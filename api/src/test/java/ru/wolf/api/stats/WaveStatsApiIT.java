package ru.wolf.api.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloProject;
import ru.wolf.api.delo.DeloProjectId;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.goal.Goal;
import ru.wolf.api.goal.GoalProject;
import ru.wolf.api.goal.GoalProjectId;
import ru.wolf.api.goal.GoalProjectRepository;
import ru.wolf.api.goal.GoalRepository;
import ru.wolf.api.lifearea.LifeArea;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

class WaveStatsApiIT extends ApiIntegrationTest {
    @Autowired UserRepository users;
    @Autowired GoalRepository goals;
    @Autowired GoalProjectRepository goalProjects;
    @Autowired DeloRepository delos;
    @Autowired DeloProjectRepository deloProjects;
    @Autowired ProjectRepository projects;
    @Autowired LifeAreaRepository areas;
    @Autowired TimeEntryRepository entries;

    @BeforeEach
    void cleanup() {
        entries.deleteAll(); goalProjects.deleteAll(); deloProjects.deleteAll(); delos.deleteAll();
        goals.deleteAll(); projects.deleteAll(); areas.deleteAll();
    }

    @Test
    void wave_returns_week_values_and_statistics() {
        User user = users.findByUsername("admin").orElseThrow();
        LifeArea area = areas.save(LifeArea.builder().user(user).name("Работа").sortOrder(0).build());
        Project project = projects.save(Project.builder().user(user).lifeArea(area).title("Проект").build());
        Goal goal = goals.save(Goal.builder().user(user).title("Цель").priority(1).build());
        goalProjects.save(GoalProject.builder().id(new GoalProjectId(goal.getId(), project.getId())).goal(goal).project(project).build());
        Delo delo = delos.save(Delo.builder().user(user).title("Дело").build());
        deloProjects.save(DeloProject.builder().id(new DeloProjectId(delo.getId(), project.getId())).delo(delo).project(project).isPrimary(true).build());
        LocalDate monday = LocalDate.now().minusWeeks(3).with(java.time.DayOfWeek.MONDAY);
        for (int i = 0; i < 3; i++) {
            LocalDateTime start = monday.plusWeeks(i).atTime(10, 0);
            entries.save(TimeEntry.builder().user(user).delo(delo).startAt(start).endAt(start.plusHours(i + 1)).status(TimeEntry.Status.DONE).build());
        }

        List<Map> response = authedAdminClient().get().uri("/api/v1/stats/wave?weeks=4")
                .exchange().expectStatus().isOk().expectBodyList(Map.class).returnResult().getResponseBody();

        Map row = response.stream().filter(item -> item.get("goalId").equals(goal.getId().intValue())).findFirst().orElseThrow();
        assertThat(((List<?>) row.get("weeks"))).hasSize(4);
        assertThat(((Number) row.get("max")).doubleValue()).isEqualTo(3.0);
        assertThat(((Number) row.get("min")).doubleValue()).isEqualTo(0.0);
        assertThat(((Number) row.get("median")).doubleValue()).isEqualTo(1.5);
    }

    @Test
    void goal_without_facts_has_zero_weeks() {
        User user = users.findByUsername("admin").orElseThrow();
        Goal goal = goals.save(Goal.builder().user(user).title("Пустая цель").priority(1).build());
        Map row = authedAdminClient().get().uri("/api/v1/stats/wave?weeks=4").exchange().expectStatus().isOk()
                .expectBodyList(Map.class).returnResult().getResponseBody().stream()
                .filter(item -> item.get("goalId").equals(goal.getId().intValue())).findFirst().orElseThrow();
        assertThat(((Number) row.get("avg")).doubleValue()).isZero();
        assertThat(((List<?>) row.get("weeks"))).hasSize(4);
    }
}

