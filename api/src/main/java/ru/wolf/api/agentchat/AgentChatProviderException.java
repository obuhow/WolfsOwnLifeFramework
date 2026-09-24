package ru.wolf.api.agentchat;

/** A provider failure is returned to the client instead of becoming a fake reply. */
public class AgentChatProviderException extends RuntimeException {
    public AgentChatProviderException(String message) {
        super(message);
    }

    public AgentChatProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
