package ru.wolf.api.morning.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import ru.wolf.api.note.Note;
import ru.wolf.api.idea.Idea;
public record MorningDigestResponse(String weekId, List<ProjectDigest> projects, List<IdeaDigest> ideas, List<GoalFactDigest> goalsFact) {}
