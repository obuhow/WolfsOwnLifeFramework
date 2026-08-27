package ru.wolf.api.checklist.dto;

public record ItemResponse(String title, Long deloId, String deloTitle, boolean done, Instant doneAt) {}
