package ru.wolf.api.agentchat.dto;

import ru.wolf.api.agentchat.AgentAction;
import ru.wolf.api.agentchat.AgentActionProposal;

import java.util.Map;

public record ProposedActionResponse(
        Long id,
        AgentAction.Type type,
        Long targetId,
        Map<String, Object> fields,
        AgentActionProposal.Status status,
        Long assistantMessageId,
        Map<String, Object> result
) {
}
