package ru.wolf.api.gantt.dto;
import java.math.BigDecimal;
/**
 * pendingHours (ticket 06, release 1.1) — сумма часов ЗАПЛАНИРОВАННЫХ (status=PLANNED,
 * ещё не подтверждённых) Записей времени проекта за эту ISO-неделю. Тот же primary/
 * ALL_PROJECTS учёт, что и factHours (см. GanttService.computeHours), только по
 * противоположному статусу. Используется полосой заполнения проекта в Ежедневнике.
 */
public record CellHours(int isoYear,int isoWeek,BigDecimal planHours,BigDecimal factHours,BigDecimal pendingHours) {}
