package ru.wolf.api.agentcontext;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentContextFormatterTest {
    @Test
    void empty_history_is_explicit_and_does_not_invent_zero_hours() {
        AgentContext.ScheduleDynamics dynamics = new AgentContext.ScheduleDynamics(
                false,
                "За выбранный период нет данных расписания: история ещё не накоплена.",
                List.of(), List.of(), List.of(), List.of(),
                new AgentContext.Trend("UNAVAILABLE", null, null, null));
        AgentContext context = new AgentContext(
                Instant.parse("2026-01-01T00:00:00Z"),
                new AgentContext.Period(LocalDate.of(2025, 7, 1), LocalDate.of(2026, 1, 1), 26),
                List.of(), List.of(), List.of(), dynamics, "pending");

        String prompt = AgentContextFormatter.format(context);

        assertThat(prompt).contains("нет данных расписания");
        assertThat(prompt).doesNotContain("факт=0");
    }

    @Test
    void prompt_has_a_hard_size_limit() {
        String huge = "x".repeat(20_000);
        AgentContext context = new AgentContext(
                Instant.now(),
                new AgentContext.Period(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 2, 2), 4),
                List.of(new AgentContext.ProjectSummary(1L, "P", "A", huge, null, null, null)),
                List.of(), List.of(),
                new AgentContext.ScheduleDynamics(false, "нет данных", List.of(), List.of(), List.of(), List.of(),
                        new AgentContext.Trend("UNAVAILABLE", null, null, null)), "pending");

        assertThat(AgentContextFormatter.format(context)).hasSizeLessThanOrEqualTo(AgentContextFormatter.MAX_PROMPT_CHARS);
    }
}
