package ru.wolf.api.focus.dto;

public record ReviewResponse(Long sessionId, Long mainDeloId, String mainDeloTitle, LocalDateTime startedAt, LocalDateTime endedAt, List<DistractionResponse> distractions) {}
