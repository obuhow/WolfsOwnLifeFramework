package ru.wolf.api.agentchat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.agentchat.dto.AgentChatResponse;
import ru.wolf.api.agentchat.dto.ChatMessageResponse;
import ru.wolf.api.agentcontext.AgentContext;
import ru.wolf.api.agentcontext.AgentContextService;
import ru.wolf.api.note.assistant.LlmDisabledException;
import ru.wolf.api.note.assistant.NotesAssistantProperties;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentChatService {

    static final int MAX_HISTORY_CHARS = 20_000;
    static final int MAX_MESSAGE_CHARS = 8_000;
    static final int MAX_RESPONSE_CHARS = 100_000;

    static final String SYSTEM_PROMPT = "Ты — встроенный управляющий агент-планировщик WOLF. "
            + "Отвечай по-русски, конкретно и кратко. Опирайся только на переданный контекст "
            + "и историю диалога; не выдумывай отсутствующие числа и факты. Если данных не хватает, "
            + "прямо скажи об этом. Термины WOLF: Проект, Дело, Запись времени, Рутина и Область жизни. "
            + "Никаких действий в системе самостоятельно: только объяснения и предложения; применение "
            + "действий будет доступно отдельным подтверждением пользователя.";

    private final UserRepository userRepository;
    private final ChatService chatService;
    private final AgentContextService contextService;
    private final AgentChatPort agent;
    private final NotesAssistantProperties properties;

    @Transactional
    public AgentChatResponse complete(String username, Long sessionId, String content) {
        if (!properties.isEnabled()) {
            throw new LlmDisabledException("LLM-функции отключены: установите wolf.llm.enabled=true");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Сообщение не может быть пустым");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        List<ChatMessage> history = chatService.agentHistory(user, sessionId);
        AgentContext context = contextService.build(user);

        List<AgentChatPort.Message> messages = new ArrayList<>(history.size() + 1);
        messages.addAll(boundedHistory(history));
        messages.add(new AgentChatPort.Message(ChatMessage.Role.USER, truncate(content.trim(), MAX_MESSAGE_CHARS)));

        AgentChatPort.Response reply;
        try {
            reply = agent.complete(new AgentChatPort.Request(
                    SYSTEM_PROMPT + "\n\n" + context.prompt(), messages));
        } catch (AgentChatProviderException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AgentChatProviderException(providerMessage(), ex);
        }

        if (reply == null || reply.content() == null || reply.content().isBlank()) {
            throw new AgentChatProviderException("LLM-провайдер вернул пустой ответ");
        }
        if (reply.content().length() > MAX_RESPONSE_CHARS) {
            throw new AgentChatProviderException("LLM-провайдер вернул слишком большой ответ");
        }

        ChatMessageResponse userMessage = chatService.appendMessage(
                user, sessionId, ChatMessage.Role.USER, content);
        ChatMessageResponse assistantMessage = chatService.appendMessage(
                user, sessionId, ChatMessage.Role.ASSISTANT, reply.content());
        return new AgentChatResponse(userMessage, assistantMessage, properties.getModel());
    }

    private String providerMessage() {
        return "LLM-провайдер временно недоступен";
    }

    private List<AgentChatPort.Message> boundedHistory(List<ChatMessage> history) {
        Deque<AgentChatPort.Message> bounded = new ArrayDeque<>();
        int chars = 0;
        for (int index = history.size() - 1; index >= 0 && chars < MAX_HISTORY_CHARS; index--) {
            ChatMessage message = history.get(index);
            String content = truncate(message.getContent(), MAX_MESSAGE_CHARS);
            int remaining = MAX_HISTORY_CHARS - chars;
            if (content.length() > remaining) {
                content = truncate(content, remaining);
            }
            if (content.isBlank()) {
                continue;
            }
            bounded.addFirst(new AgentChatPort.Message(message.getRole(), content));
            chars += content.length();
        }
        return List.copyOf(bounded);
    }

    private String truncate(String value, int maxChars) {
        if (value.length() <= maxChars) {
            return value;
        }
        String suffix = "\n[Сообщение сокращено для контекста агента]";
        if (maxChars <= suffix.length()) {
            return value.substring(0, maxChars);
        }
        return value.substring(0, maxChars - suffix.length()) + suffix;
    }
}
