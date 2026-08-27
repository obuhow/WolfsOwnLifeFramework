package ru.wolf.api.checklist.dto;

public record DistractionResponse(LocalDateTime at, String target, Integer minutes) {}
