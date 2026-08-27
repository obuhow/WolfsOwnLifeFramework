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
package ru.wolf.api.importer;

import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import ru.wolf.api.importer.dto.LlmParseRequest;
import ru.wolf.api.importer.dto.LlmParseResponse;
import ru.wolf.api.note.assistant.NotesAssistantProperties;

@Profile("!test")
@Component
public class HttpImportParserLlmAdapter implements ImportParserLlmPort {

    private final RestClient client;
    private final NotesAssistantProperties properties;

    public HttpImportParserLlmAdapter(RestClient.Builder builder, NotesAssistantProperties properties) {
        this.properties = properties;
        this.client = builder.baseUrl(properties.getUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public LlmParseResponse parse(LlmParseRequest request) {
        CompletionResponse response = client.post()
                .uri("/chat/completions")
                .body(requestBody(request))
                .retrieve()
                .body(CompletionResponse.class);
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            return new LlmParseResponse(null);
        }
        return new LlmParseResponse(response.choices().get(0).message().content());
    }

    private Map<String, Object> requestBody(LlmParseRequest request) {
        Map<String, Object> responseFormat = Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "wolf_import_candidates",
                        "strict", true,
                        "schema", parseSchema(request.jsonSchema())));
        Map<String, Object> userMessage = Map.of(
                "role", "user",
                "content", request.userText());
        List<Object> messages = request.systemPrompt() == null || request.systemPrompt().isBlank()
                ? List.of(userMessage)
                : List.of(Map.of("role", "system", "content", request.systemPrompt()), userMessage);
        return Map.of(
                "model", properties.getModel(),
                "messages", messages,
                "response_format", responseFormat);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSchema(String jsonSchema) {
        if (jsonSchema == null || jsonSchema.isBlank()) {
            return Map.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(jsonSchema, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Невалидная JSON-схема разбора импорта", e);
        }
    }

    private record CompletionRequest(String model, List<?> messages, Map<String, Object> responseFormat) {
    }

    private record CompletionResponse(List<Choice> choices) {
    }

    private record Choice(Message message) {
    }

    private record Message(String role, String content) {
    }
}
