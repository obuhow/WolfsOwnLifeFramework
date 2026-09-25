package ru.wolf.api.agentchat.dto;

import ru.wolf.api.agentchat.ChatMessage;

import java.time.Instant;

public record ChatMessageResponse(
        Long id,
        Long sessionId,
        ChatMessage.Role role,
        String content,
        Instant createdAt,
        ContextTransparencyResponse contextTransparency
) {
    public static ChatMessageResponse from(ChatMessage message) {
        return from(message, null);
    }

    public static ChatMessageResponse from(
            ChatMessage message, ContextTransparencyResponse contextTransparency) {
        return new ChatMessageResponse(
                message.getId(),
                message.getSession().getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt(),
                contextTransparency
        );
    }
}
