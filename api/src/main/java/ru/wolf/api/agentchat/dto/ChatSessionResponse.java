package ru.wolf.api.agentchat.dto;

import ru.wolf.api.agentchat.ChatSession;

import java.time.Instant;

public record ChatSessionResponse(
        Long id,
        Instant createdAt,
        Instant updatedAt
) {
    public static ChatSessionResponse from(ChatSession session) {
        return new ChatSessionResponse(session.getId(), session.getCreatedAt(), session.getUpdatedAt());
    }
}
