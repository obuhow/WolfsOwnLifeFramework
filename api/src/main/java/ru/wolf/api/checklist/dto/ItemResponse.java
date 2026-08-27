package ru.wolf.api.checklist.dto;
import java.time.Instant;



public record ItemResponse(String title, Long deloId, String deloTitle, boolean done, Instant doneAt) {}
