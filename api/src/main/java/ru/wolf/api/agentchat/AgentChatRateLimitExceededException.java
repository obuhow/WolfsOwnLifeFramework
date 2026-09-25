package ru.wolf.api.agentchat;

public class AgentChatRateLimitExceededException extends RuntimeException {

    public AgentChatRateLimitExceededException(String message) {
        super(message);
    }
}