package ru.wolf.api.agentchat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentChatRequest(
        @NotBlank @Size(max = 20_000) String content
) {
}
