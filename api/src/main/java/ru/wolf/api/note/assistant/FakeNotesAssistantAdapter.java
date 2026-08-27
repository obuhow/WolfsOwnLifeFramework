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
package ru.wolf.api.note.assistant;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class FakeNotesAssistantAdapter implements AssistantPort {

    private final AtomicReference<String> transcriptionResponse = new AtomicReference<>("Тестовая транскрипция");
    private final AtomicReference<String> summaryResponse = new AtomicReference<>("Тестовая сводка");
    private final AtomicReference<String> suggestionResponse = new AtomicReference<>("Тестовая подсказка");

    @Override
    public String transcribe(String audioRef) {
        return transcriptionResponse.get();
    }

    @Override
    public String summarize(Long projectId, List<Long> noteIds) {
        return summaryResponse.get();
    }

    @Override
    public String suggest(Long projectId, List<String> topics) {
        return suggestionResponse.get();
    }

    public void setTranscriptionResponse(String response) {
        transcriptionResponse.set(response);
    }

    public void setSummaryResponse(String response) {
        summaryResponse.set(response);
    }

    public void setSuggestionResponse(String response) {
        suggestionResponse.set(response);
    }

    public void reset() {
        transcriptionResponse.set("Тестовая транскрипция");
        summaryResponse.set("Тестовая сводка");
        suggestionResponse.set("Тестовая подсказка");
    }
}
