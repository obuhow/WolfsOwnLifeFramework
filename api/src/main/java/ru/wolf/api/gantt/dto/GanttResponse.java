package ru.wolf.api.gantt.dto;
import java.util.List;
public record GanttResponse(String hourAccountingMode,String timezone,String rangeStart,String rangeEndExclusive,List<WeekColumn> weeks,List<ProjectRow> projects) {}
