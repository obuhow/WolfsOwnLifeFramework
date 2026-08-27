package ru.wolf.api.stats.dto;

import java.math.BigDecimal;

public record WeekFact(String weekId, BigDecimal factHours) {}
