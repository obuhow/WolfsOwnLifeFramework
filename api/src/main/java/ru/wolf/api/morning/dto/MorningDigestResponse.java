package ru.wolf.api.morning.dto;

import java.util.List;

public record MorningDigestResponse(String weekId, List<ProjectDigest> projects, List<IdeaDigest> ideas, List<GoalFactDigest> goalsFact) {}
