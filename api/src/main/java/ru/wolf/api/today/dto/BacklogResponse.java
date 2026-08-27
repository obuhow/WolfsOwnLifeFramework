package ru.wolf.api.today.dto;

import java.math.BigDecimal;
import java.util.List;

public record BacklogResponse(String weekId, List<ItemResponse> items, BigDecimal totalPlanned, BigDecimal totalFact) {}
