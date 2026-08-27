package ru.wolf.api.routine.dto;

public record GoalLinkResponse(Long goalId, String goalTitle) {
    /** Compatibility accessor for legacy callers. */
    public Long getGoalId() { return goalId(); }

    /** Compatibility accessor for legacy callers. */
    public String getGoalTitle() { return goalTitle(); }
}
