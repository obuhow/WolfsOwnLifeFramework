package ru.wolf.api.morning.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import ru.wolf.api.note.Note;
import ru.wolf.api.idea.Idea;
public record NoteDigest(Long id, Note.Author author, String body, String[] tags, Instant createdAt, Instant updatedAt) {}
