package ru.wolf.api.agentchat;

import java.util.LinkedHashMap;
import java.util.Map;

/** One safe, user-confirmable mutation proposed by the planning agent. */
public record AgentAction(Type type, Long targetId, Map<String, Object> fields) {

    public AgentAction {
        if (type == null) {
            throw new IllegalArgumentException("Тип действия агента обязателен");
        }
        if (type.requiresTarget() && targetId == null) {
            throw new IllegalArgumentException("Действию нужен targetId");
        }
        if (fields == null) {
            fields = Map.of();
        } else {
            fields = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }
    }

    public enum Type {
        CREATE_DELO,
        UPDATE_DELO,
        DELETE_DELO,
        CREATE_PROJECT,
        UPDATE_PROJECT,
        DELETE_PROJECT,
        CREATE_TIME_ENTRY,
        UPDATE_TIME_ENTRY,
        DELETE_TIME_ENTRY,
        APPLY_RECURRENCE;

        public boolean requiresTarget() {
            return name().startsWith("UPDATE_") || name().startsWith("DELETE_") || this == APPLY_RECURRENCE;
        }
    }
}
