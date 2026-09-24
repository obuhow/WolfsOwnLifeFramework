package ru.wolf.api.agentchat.dto;

import ru.wolf.api.agentchat.ChatMessage;

import java.time.Instant;

public record ChatMessageResponse(
        Long id,
        Long sessionId,
        ChatMessage.Role role,
        String content,
        Instant createdAt
) {
    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getSession().getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
