package ru.wolf.api.focus.dto;

public record DistractionResponse(Long id, LocalDateTime at, Long deloId, String deloTitle, String text, Integer suggestedMinutes, boolean applied, Integer appliedMinutes) {}
