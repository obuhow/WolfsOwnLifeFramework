package ru.wolf.api.goal;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.wolf.api.support.ApiIntegrationTest;

class GoalMetricIncrementApiIT extends ApiIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired GoalRepository goals;
    @org.springframework.beans.factory.annotation.Autowired GoalMetricRepository metrics;
    @org.springframework.beans.factory.annotation.Autowired ru.wolf.api.user.UserRepository users;

    @BeforeEach void clean() { metrics.deleteAll(); goals.deleteAll(); }

    @Test
    void increment_updates_value_and_rejects_below_zero_or_above_target() {
        var user = users.findByUsername("admin").orElseThrow();
        Goal goal = goals.save(Goal.builder().user(user).title("Метрики").priority(1).archived(false).build());
        GoalMetric metric = metrics.save(GoalMetric.builder().goal(goal).kind("заявки").value(new BigDecimal("3")).targetValue(new BigDecimal("500")).at(java.time.LocalDateTime.now()).build());
        WebTestClient client = authedAdminClient();
        client.patch().uri("/api/v1/goals/{id}/metrics/{metricId}/increment", goal.getId(), metric.getId()).contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("amount", 1)).exchange().expectStatus().isOk();
        client.patch().uri("/api/v1/goals/{id}/metrics/{metricId}/increment", goal.getId(), metric.getId()).contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("amount", 5)).exchange().expectStatus().isOk();
        assertThat(metrics.findById(metric.getId()).orElseThrow().getValue()).isEqualByComparingTo("9");
        client.patch().uri("/api/v1/goals/{id}/metrics/{metricId}/increment", goal.getId(), metric.getId()).contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("amount", -10)).exchange().expectStatus().isBadRequest();
        client.patch().uri("/api/v1/goals/{id}/metrics/{metricId}/increment", goal.getId(), metric.getId()).contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("amount", 600)).exchange().expectStatus().isBadRequest();
    }
}
