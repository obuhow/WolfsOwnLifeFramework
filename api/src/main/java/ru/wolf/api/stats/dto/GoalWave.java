package ru.wolf.api.stats.dto;

public record GoalWave(Long goalId, String title, List<WeekFact> weeks, double avg, double median, double max, double min) {}
