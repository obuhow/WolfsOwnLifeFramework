package ru.wolf.api.gantt.dto;
public record WeekColumn(int isoYear,int isoWeek,String weekStart,String weekEndExclusive,String monthLabel,int month,int calendarYear,boolean current) {}
