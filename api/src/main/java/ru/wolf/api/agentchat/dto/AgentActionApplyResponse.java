package ru.wolf.api.agentchat.dto;

import ru.wolf.api.agentchat.AgentActionProposal;

import java.util.Map;

public record AgentActionApplyResponse(
        Long id,
        AgentActionProposal.Status status,
        boolean applied,
        Map<String, Object> result
) {
}
