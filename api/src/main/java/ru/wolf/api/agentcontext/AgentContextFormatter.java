package ru.wolf.api.agentcontext;

import java.math.BigDecimal;
import java.util.StringJoiner;

/** Converts the structured context into a small, human-readable model prompt. */
public final class AgentContextFormatter {
    public static final int MAX_PROMPT_CHARS = 12_000;

    private AgentContextFormatter() {
    }

    public static String format(AgentContext context) {
        StringBuilder out = new StringBuilder();
        line(out, "Контекст WOLF для управляющего агента");
        line(out, "Период: " + context.period().from() + " — " + context.period().toExclusive()
                + " (" + context.period().weeks() + " недель)");
        line(out, "Сырые Записи времени не переданы; числа ниже посчитаны кодом.");
        line(out, "Активные проекты:");
        for (AgentContext.ProjectSummary project : context.projects()) {
            line(out, "- #%d %s | область: %s | план за весь срок: %s | даты: %s — %s | описание: %s"
                    .formatted(project.id(), project.title(), project.lifeArea(), value(project.totalPlanHours()),
                            value(project.startDate()), value(project.endDate()), shorten(project.description(), 240)));
        }
        line(out, "Активные цели:");
        for (AgentContext.GoalSummary goal : context.goals()) {
            line(out, "- #%d %s | приоритет: %d | проекты: %s | описание: %s"
                    .formatted(goal.id(), goal.title(), goal.priority(), join(goal.projectTitles()), shorten(goal.description(), 240)));
        }
        line(out, "Активные рутины:");
        for (AgentContext.RoutineSummary routine : context.routines()) {
            line(out, "- #%d %s | недельная квота: %s | расписание: %s | описание: %s"
                    .formatted(routine.id(), routine.title(), value(routine.weeklyHours()), join(routine.schedule()), shorten(routine.description(), 180)));
        }
        line(out, "Динамика расписания:");
        AgentContext.ScheduleDynamics dynamics = context.dynamics();
        if (!dynamics.historyAvailable()) {
            line(out, "- " + dynamics.historyNote());
        } else {
            line(out, "- По проектам: " + hours(dynamics.projectHours()));
            line(out, "- По областям жизни: " + hours(dynamics.lifeAreaHours()));
            line(out, "- По рутинам: " + hours(dynamics.routineHours()));
            line(out, "- Тренд факта: %s, изменение второй половины к первой: %s ч"
                    .formatted(dynamics.trend().direction(), value(dynamics.trend().changeHours())));
            line(out, "- Недели: " + dynamics.weeks().stream()
                    .map(week -> week.weekId() + " план=" + value(week.plannedHours()) + " факт=" + value(week.factHours())
                            + " сетка=" + value(week.pendingHours()))
                    .reduce((left, right) -> left + "; " + right).orElse("нет данных"));
        }
        if (out.length() > MAX_PROMPT_CHARS) {
            return out.substring(0, MAX_PROMPT_CHARS - 80) + "\n[Контекст сокращён до безопасного лимита. Запросите детали через инструмент.]";
        }
        return out.toString().trim();
    }

    private static String hours(java.util.List<AgentContext.Hours> values) {
        if (values.isEmpty()) return "нет данных";
        StringJoiner joiner = new StringJoiner("; ");
        for (AgentContext.Hours item : values) {
            joiner.add(item.label() + " (план=" + value(item.planned()) + ", факт=" + value(item.fact())
                    + ", запланировано в сетке=" + value(item.pending()) + ")");
        }
        return joiner.toString();
    }

    private static void line(StringBuilder out, String line) { out.append(line).append('\n'); }
    private static String join(java.util.List<String> values) { return values.isEmpty() ? "нет" : String.join(", ", values); }
    private static String value(Object value) { return value == null ? "нет данных" : value.toString(); }
    private static String value(BigDecimal value) { return value == null ? "нет данных" : value.stripTrailingZeros().toPlainString(); }
    private static String shorten(String value, int max) {
        if (value == null || value.isBlank()) return "нет описания";
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max - 1) + "…";
    }
}
