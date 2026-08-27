/*
 * WOLF — Wolf's Own Life Framework
 * Copyright (C) 2025 Pavel Obukhov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.note.dto;

import ru.wolf.api.note.Note;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public record NoteResponse(
        Long id,
        Long projectId,
        String projectTitle,
        Long deloId,
        String deloTitle,
        Note.Author author,
        String body,
        List<String> tags,
        String audioRef,
        Instant createdAt,
        Instant updatedAt
) {

    public static NoteResponse from(Note note) {
        return new NoteResponse(
                note.getId(),
                note.getProject() == null ? null : note.getProject().getId(),
                note.getProject() == null ? null : note.getProject().getTitle(),
                note.getDelo() == null ? null : note.getDelo().getId(),
                note.getDelo() == null ? null : note.getDelo().getTitle(),
                note.getAuthor(),
                note.getBody(),
                Arrays.asList(note.getTags()),
                note.getAudioAttachment() == null ? null : note.getAudioAttachment().getAudioRef(),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }
}
