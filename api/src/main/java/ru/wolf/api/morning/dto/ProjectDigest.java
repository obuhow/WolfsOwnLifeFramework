package ru.wolf.api.morning.dto;

import java.util.List;

/**
 * A project card in the morning digest.
 *
 * <p>Release 1.0, ticket 05: {@code parentId}/{@code depth} carry the project tree so the
 * Morning ritual can render nesting (Проект WOLF → WOLF MVP) with indentation and collapse.
 * The list stays flat but pre-ordered (roots by title, then each root's children by title);
 * {@code depth} counts visible ancestors, {@code parentId} points at the nearest visible parent
 * (null for a displayed root).
 */
public record ProjectDigest(
        Long id,
        Long parentId,
        int depth,
        String title,
        List<NoteDigest> lastNotes,
        List<DeloDigest> topDelos) {}
