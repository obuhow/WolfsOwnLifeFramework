package ru.wolf.api.stats.dto;
import java.util.List;



public record GoalWave(Long goalId, String title, List<WeekFact> weeks, double avg, double median, double max, double min) {}
