package ru.wolf.api.agentchat;

public class ChatSessionNotFoundException extends RuntimeException {
    public ChatSessionNotFoundException() {
        super("Чат не найден");
    }
}
