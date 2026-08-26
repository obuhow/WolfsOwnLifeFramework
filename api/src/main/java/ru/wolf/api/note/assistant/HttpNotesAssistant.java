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

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Profile("!test")
public class HttpNotesAssistant implements NotesAssistant {

    private final RestClient client;
    private final NotesAssistantProperties properties;

    public HttpNotesAssistant(RestClient.Builder builder, NotesAssistantProperties properties) {
        this.properties = properties;
        this.client = builder.baseUrl(properties.getUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String transcribe(String audioRef) {
        return complete("Transcribe this audio reference: " + audioRef);
    }

    @Override
    public String summarize(Long projectId, List<Long> noteIds) {
        return complete("Summarize project " + projectId + " notes " + noteIds);
    }

    @Override
    public String suggest(Long projectId, List<String> topics) {
        return complete("Suggest next steps for project " + projectId + " about " + topics);
    }

    private String complete(String prompt) {
        CompletionResponse response = client.post()
                .uri("/chat/completions")
                .body(new CompletionRequest(properties.getModel(), List.of(new Message("user", prompt))))
                .retrieve()
                .body(CompletionResponse.class);
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("LLM вернул пустой ответ");
        }
        return response.choices().get(0).message().content();
    }

    private record CompletionRequest(String model, List<Message> messages) {
    }

    private record Message(String role, String content) {
    }

    private record CompletionResponse(List<Choice> choices) {
    }

    private record Choice(Message message) {
    }
}
