package ru.wolf.api.agentchat.dto;

public record AgentChatResponse(
        ChatMessageResponse userMessage,
        ChatMessageResponse assistantMessage,
        String model,
        ProposedActionResponse proposedAction,
        ContextTransparencyResponse contextTransparency
) {
    public AgentChatResponse(ChatMessageResponse userMessage,
                             ChatMessageResponse assistantMessage,
                             String model,
                             ProposedActionResponse proposedAction) {
        this(userMessage, assistantMessage, model, proposedAction, null);
    }

    public AgentChatResponse(ChatMessageResponse userMessage,
                             ChatMessageResponse assistantMessage,
                             String model) {
        this(userMessage, assistantMessage, model, null, null);
    }
}
