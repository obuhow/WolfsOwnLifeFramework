package ru.wolf.api.note.assistant;

import java.util.List;

/** Application port for LLM-backed note operations. */
public interface NotesAssistant {

    String transcribe(String audioRef);

    String summarize(Long projectId, List<Long> noteIds);

    String suggest(Long projectId, List<String> topics);
}
