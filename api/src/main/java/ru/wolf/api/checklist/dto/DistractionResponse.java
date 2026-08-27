package ru.wolf.api.checklist.dto;
import java.time.LocalDateTime;



public record DistractionResponse(LocalDateTime at, String target, Integer minutes) {}
