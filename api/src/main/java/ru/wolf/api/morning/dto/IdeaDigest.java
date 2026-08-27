package ru.wolf.api.morning.dto;

import ru.wolf.api.idea.Idea;

public record IdeaDigest(Long id, String title, String description, Idea.Category category) {}
