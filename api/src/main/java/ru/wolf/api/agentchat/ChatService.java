/*
 * WOLF — Wolf's Own Life Framework
 * Copyright (C) 2025 Pavel Obukhov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.agentchat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.agentchat.dto.ChatMessageRequest;
import ru.wolf.api.agentchat.dto.ChatMessageResponse;
import ru.wolf.api.agentchat.dto.ChatSessionResponse;
import ru.wolf.api.agentchat.dto.ContextTransparencyResponse;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    static final int DEFAULT_PAGE_SIZE = 100;
    static final int MAX_PAGE_SIZE = 200;

    private final UserRepository userRepository;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> listSessions(String username) {
        User user = currentUser(username);
        return sessionRepository.findByUserOrderByUpdatedAtDescIdDesc(user).stream()
                .map(ChatSessionResponse::from)
                .toList();
    }

    @Transactional
    public ChatSessionResponse createSession(String username) {
        ChatSession session = ChatSession.builder()
                .user(currentUser(username))
                .build();
        return ChatSessionResponse.from(sessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public ChatSessionResponse getSession(String username, Long sessionId) {
        return ChatSessionResponse.from(findSession(currentUser(username), sessionId));
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> listMessages(String username, Long sessionId, int page, int limit) {
        validatePage(page, limit);
        ChatSession session = findSession(currentUser(username), sessionId);
        return messageRepository.findBySessionOrderByCreatedAtAscIdAsc(session, PageRequest.of(page, limit))
                .getContent()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ChatMessageResponse appendMessage(String username, Long sessionId, ChatMessageRequest request) {
        if (request.role() != ChatMessage.Role.USER) {
            throw new IllegalArgumentException("Внешний клиент может добавлять только сообщения пользователя");
        }
        User user = currentUser(username);
        return appendMessage(user, sessionId, request.role(), request.content());
    }

    /** Shared storage seam for the agent endpoint; not exposed as an HTTP role setter. */
    @Transactional
    ChatMessageResponse appendMessage(
            User user, Long sessionId, ChatMessage.Role role, String content) {
        return appendMessage(user, sessionId, role, content, null);
    }

    /** Stores the exact safe context summary alongside the assistant message. */
    @Transactional
    ChatMessageResponse appendMessage(
            User user, Long sessionId, ChatMessage.Role role, String content,
            ContextTransparencyResponse contextTransparency) {
        if (role == null) throw new IllegalArgumentException("Роль сообщения обязательна");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Сообщение не может быть пустым");
        }
        ChatSession session = findSession(user, sessionId);
        ChatMessage message = ChatMessage.builder()
                .session(session)
                .role(role)
                .content(content.trim())
                .contextTransparencyJson(writeContextTransparency(contextTransparency))
                .createdAt(Instant.now())
                .build();
        ChatMessage saved = messageRepository.save(message);
        session.setUpdatedAt(Instant.now());
        sessionRepository.save(session);
        return toResponse(saved);
    }

    /** Bounded history for one agent request, oldest message first. */
    @Transactional(readOnly = true)
    List<ChatMessage> agentHistory(User user, Long sessionId) {
        ChatSession session = findSession(user, sessionId);
        List<ChatMessage> recent = new ArrayList<>(
                messageRepository.findTop100BySessionOrderByCreatedAtDescIdDesc(session));
        Collections.reverse(recent);
        return recent;
    }

    ChatSession sessionEntity(User user, Long sessionId) {
        return findSession(user, sessionId);
    }

    ChatMessage messageEntity(User user, Long sessionId, Long messageId) {
        ChatSession session = findSession(user, sessionId);
        return messageRepository.findById(messageId)
                .filter(message -> message.getSession().getId().equals(session.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Сообщение чата не найдено"));
    }

    private void validatePage(int page, int limit) {
        if (page < 0) throw new IllegalArgumentException("Номер страницы не может быть отрицательным");
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Лимит истории должен быть от 1 до " + MAX_PAGE_SIZE);
        }
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private ChatSession findSession(User user, Long sessionId) {
        return sessionRepository.findByUserAndId(user, sessionId)
                .orElseThrow(ChatSessionNotFoundException::new);
    }

    private ChatMessageResponse toResponse(ChatMessage message) {
        return ChatMessageResponse.from(message, readContextTransparency(message.getContextTransparencyJson()));
    }

    private String writeContextTransparency(ContextTransparencyResponse contextTransparency) {
        if (contextTransparency == null) return null;
        try {
            return objectMapper.writeValueAsString(contextTransparency);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Не удалось сохранить прозрачность контекста", ex);
        }
    }

    private ContextTransparencyResponse readContextTransparency(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, ContextTransparencyResponse.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Не удалось прочитать прозрачность контекста", ex);
        }
    }
}
