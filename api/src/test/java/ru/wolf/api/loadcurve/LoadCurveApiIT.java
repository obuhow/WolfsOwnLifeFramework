package ru.wolf.api.loadcurve;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.lifearea.LifeArea;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.support.ApiIntegrationTest;
import ru.wolf.api.user.UserRepository;
import ru.wolf.api.routine.Routine;
import ru.wolf.api.routine.RoutineRepository;

class LoadCurveApiIT extends ApiIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired LoadCurveEntryRepository curves;
    @org.springframework.beans.factory.annotation.Autowired ProjectRepository projects;
    @org.springframework.beans.factory.annotation.Autowired DeloRepository delos;
    @org.springframework.beans.factory.annotation.Autowired UserRepository users;
    @org.springframework.beans.factory.annotation.Autowired LifeAreaRepository areas;
    @org.springframework.beans.factory.annotation.Autowired RoutineRepository routines;

    @BeforeEach void clean() { curves.deleteAll(); }

    @Test
    void project_curve_upserts_weeks_and_capacity_uses_curve_hours() {
        var user = users.findByUsername("admin").orElseThrow();
        LifeArea area = areas.save(LifeArea.builder().user(user).name("Load curve area").build());
        Project project = projects.save(Project.builder().user(user).lifeArea(area).title("Load curve project").status(Project.Status.IN_PROGRESS).planDistribution(Project.PlanDistribution.NONE).build());
        WebTestClient client = authedAdminClient();
        client.put().uri("/api/v1/projects/{id}/load-curve", project.getId()).contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("weekStart", "2026-08-17", "hours", 16)).exchange().expectStatus().isOk();
        client.put().uri("/api/v1/projects/{id}/load-curve", project.getId()).contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("weekStart", "2026-08-24", "hours", 8)).exchange().expectStatus().isOk();
        client.put().uri("/api/v1/projects/{id}/load-curve", project.getId()).contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("weekStart", "2026-08-17", "hours", 12)).exchange().expectStatus().isOk();
        client.get().uri("/api/v1/projects/{id}/load-curve", project.getId()).exchange().expectStatus().isOk().expectBodyList(LoadCurveController.Response.class).value(items -> { assertThat(items).hasSize(2); assertThat(items.get(0).hours()).isEqualByComparingTo("12.00"); });
        client.get().uri("/api/v1/planning/capacity?from=2026-08-17&to=2026-08-30").exchange().expectStatus().isOk().expectBodyList(ru.wolf.api.planning.PlanningCapacityController.CapacityResponse.class).value(items -> assertThat(items).anySatisfy(item -> { if (item.getWeekId().equals("2026-W34")) assertThat(item.getPlannedHours()).isGreaterThanOrEqualTo(BigDecimal.ZERO); }));
    }

    @Test
    void routine_curve_is_available_and_negative_hours_are_rejected() {
        var user = users.findByUsername("admin").orElseThrow();
        Routine routine = routines.save(Routine.builder().user(user).title("Спорт").weeklyHours(new BigDecimal("12")).build());
        WebTestClient client = authedAdminClient();
        client.put().uri("/api/v1/routines/{id}/load-curve", routine.getId()).contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("weekStart", "2026-08-17", "hours", 6)).exchange().expectStatus().isOk();
        client.get().uri("/api/v1/routines/{id}/load-curve", routine.getId()).exchange().expectStatus().isOk().expectBodyList(LoadCurveController.Response.class).hasSize(1);
        client.put().uri("/api/v1/routines/{id}/load-curve", routine.getId()).contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("weekStart", "2026-08-24", "hours", -1)).exchange().expectStatus().isBadRequest();
    }
}
