package ru.wolf.api.agentchat.dto;

public record AgentChatResponse(
        ChatMessageResponse userMessage,
        ChatMessageResponse assistantMessage,
        String model,
        ProposedActionResponse proposedAction
) {
    public AgentChatResponse(ChatMessageResponse userMessage,
                             ChatMessageResponse assistantMessage,
                             String model) {
        this(userMessage, assistantMessage, model, null);
    }
}
