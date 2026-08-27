package ru.wolf.api.focus.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ReviewResponse(Long sessionId, Long mainDeloId, String mainDeloTitle, LocalDateTime startedAt, LocalDateTime endedAt, List<DistractionResponse> distractions) {}
