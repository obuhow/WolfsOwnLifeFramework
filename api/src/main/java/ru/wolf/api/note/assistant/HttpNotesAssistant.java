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
