package ru.wolf.api.focus.dto;

import java.time.LocalDateTime;

public record StartRequest(Long deloId, LocalDateTime startedAt) {}
