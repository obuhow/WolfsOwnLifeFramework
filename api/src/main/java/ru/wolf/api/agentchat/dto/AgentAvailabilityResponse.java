package ru.wolf.api.agentchat.dto;

/** Public capability state for the chat panel; provider credentials are never exposed. */
public record AgentAvailabilityResponse(boolean available, String reason, String model) {
}
