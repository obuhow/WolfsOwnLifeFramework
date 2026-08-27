package ru.wolf.api.focus.dto;

import java.time.LocalDateTime;

public record DistractionRequest(Long deloId, String text, LocalDateTime at, Integer minutes) {}
