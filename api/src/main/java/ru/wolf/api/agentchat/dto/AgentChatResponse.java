package ru.wolf.api.agentchat.dto;

public record AgentChatResponse(
        ChatMessageResponse userMessage,
        ChatMessageResponse assistantMessage,
        String model
) {
}
