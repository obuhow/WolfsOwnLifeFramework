package ru.wolf.api.focus.dto;

import java.time.LocalDateTime;

public record Occupied(LocalDateTime slot, String deloTitle) {}
