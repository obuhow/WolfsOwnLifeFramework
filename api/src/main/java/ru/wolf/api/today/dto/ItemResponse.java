package ru.wolf.api.today.dto;

import java.math.BigDecimal;

public record ItemResponse(Long deloId, String title, BigDecimal plannedHours, BigDecimal factHours, String projectTitle) {}
