package ru.wolf.api.agentchat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.wolf.api.agentchat.ChatMessage;

public record ChatMessageRequest(
        @NotNull ChatMessage.Role role,
        @NotBlank @Size(max = 100_000) String content
) {
}
