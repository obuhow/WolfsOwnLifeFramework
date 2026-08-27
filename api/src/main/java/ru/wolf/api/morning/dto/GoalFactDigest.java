package ru.wolf.api.morning.dto;

import java.math.BigDecimal;

public record GoalFactDigest(Long goalId, String title, BigDecimal budgetHours, BigDecimal factHours, String weekId) {}
