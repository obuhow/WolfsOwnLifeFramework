package ru.wolf.api.focus.dto;

public record Change(LocalDateTime slot, Long sourceDeloId, String sourceDeloTitle, Long targetDeloId, String targetDeloTitle) {}
