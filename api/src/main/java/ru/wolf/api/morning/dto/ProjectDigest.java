package ru.wolf.api.morning.dto;

import java.util.List;

public record ProjectDigest(Long id, String title, List<NoteDigest> lastNotes, List<DeloDigest> topDelos) {}
