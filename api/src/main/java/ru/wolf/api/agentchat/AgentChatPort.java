package ru.wolf.api.agentchat;

import java.util.List;

/** Application port for the single built-in planning agent. */
public interface AgentChatPort {

    Response complete(Request request);

    record Request(String systemPrompt, List<Message> messages) {
        public Request {
            if (systemPrompt == null || systemPrompt.isBlank()) {
                throw new IllegalArgumentException("Системный prompt агента обязателен");
            }
            messages = List.copyOf(messages);
        }
    }

    record Message(ChatMessage.Role role, String content) {
        public Message {
            if (role == null) throw new IllegalArgumentException("Роль сообщения обязательна");
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("Сообщение агента не может быть пустым");
            }
        }
    }

    record Response(String content) {
        public Response {
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("Ответ агента не может быть пустым");
            }
        }
    }
}
