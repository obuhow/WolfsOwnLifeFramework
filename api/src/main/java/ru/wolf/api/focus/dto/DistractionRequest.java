package ru.wolf.api.focus.dto;

public record DistractionRequest(Long deloId, String text, LocalDateTime at, Integer minutes) {}
