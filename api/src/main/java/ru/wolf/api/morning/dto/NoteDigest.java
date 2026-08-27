package ru.wolf.api.morning.dto;

import ru.wolf.api.note.Note;
import java.time.Instant;

public record NoteDigest(Long id, Note.Author author, String body, String[] tags, Instant createdAt, Instant updatedAt) {}
