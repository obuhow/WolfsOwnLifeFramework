package ru.wolf.api.morning.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import ru.wolf.api.note.Note;
import ru.wolf.api.idea.Idea;
public record GoalFactDigest(Long goalId, String title, BigDecimal budgetHours, BigDecimal factHours, String weekId) {}
