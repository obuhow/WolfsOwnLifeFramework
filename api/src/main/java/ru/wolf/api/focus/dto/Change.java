package ru.wolf.api.focus.dto;

import java.time.LocalDateTime;

public record Change(LocalDateTime slot, Long sourceDeloId, String sourceDeloTitle, Long targetDeloId, String targetDeloTitle) {}
