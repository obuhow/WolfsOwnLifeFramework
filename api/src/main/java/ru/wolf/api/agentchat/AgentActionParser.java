package ru.wolf.api.agentchat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/** Parses the provider envelope while keeping plain-text provider responses compatible. */
public class AgentActionParser {

    private final ObjectMapper objectMapper;

    public AgentActionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AgentChatPort.Response parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Ответ агента не может быть пустым");
        }
        String candidate = stripCodeFence(raw.trim());
        try {
            JsonNode root = objectMapper.readTree(candidate);
            if (root == null || !root.isObject() || (!root.has("text") && !root.has("reply"))) {
                return new AgentChatPort.Response(raw.trim());
            }
            String text = root.hasNonNull("text") ? root.get("text").asText() : root.get("reply").asText();
            if (text.isBlank()) {
                throw new IllegalArgumentException("Ответ агента не содержит текста");
            }
            JsonNode actionNode = root.get("action");
            return new AgentChatPort.Response(text, actionNode == null || actionNode.isNull()
                    ? null : parseAction(actionNode));
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            // A provider may still return a normal prose answer despite the protocol instruction.
            return new AgentChatPort.Response(raw.trim());
        }
    }

    private AgentAction parseAction(JsonNode node) {
        if (!node.isObject() || !node.has("type") || !node.get("type").isTextual()) {
            throw new IllegalArgumentException("Предлагаемое действие агента имеет некорректный формат");
        }
        AgentAction.Type type;
        try {
            type = AgentAction.Type.valueOf(node.get("type").asText().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Неизвестный тип действия агента", ex);
        }
        Long targetId = node.hasNonNull("targetId") ? node.get("targetId").asLong() : null;
        Map<String, Object> fields = new LinkedHashMap<>();
        JsonNode fieldsNode = node.get("fields");
        if (fieldsNode != null && !fieldsNode.isNull()) {
            if (!fieldsNode.isObject()) {
                throw new IllegalArgumentException("Поля действия агента должны быть объектом");
            }
            fields = objectMapper.convertValue(fieldsNode, Map.class);
        }
        return new AgentAction(type, targetId, fields);
    }

    private String stripCodeFence(String value) {
        if (value.startsWith("```") && value.endsWith("```")) {
            int firstLineEnd = value.indexOf('\n');
            return firstLineEnd >= 0 ? value.substring(firstLineEnd + 1, value.length() - 3).trim() : value;
        }
        return value;
    }
}
