package ru.wolf.api.agentchat;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.wolf.api.note.assistant.NotesAssistantProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Profile("!test")
public class HttpAgentChatAdapter implements AgentChatPort {

    private final RestClient client;
    private final NotesAssistantProperties properties;
    private final AgentActionParser actionParser;

    public HttpAgentChatAdapter(RestClient.Builder builder, NotesAssistantProperties properties) {
        this.properties = properties;
        this.actionParser = new AgentActionParser(new ObjectMapper());
        this.client = builder.baseUrl(properties.getUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public Response complete(Request request) {
        try {
            CompletionResponse response = client.post()
                    .uri("/chat/completions")
                    .body(Map.of(
                            "model", properties.getModel(),
                            "messages", requestBodyMessages(request)))
                    .retrieve()
                    .body(CompletionResponse.class);
            if (response == null || response.choices() == null || response.choices().isEmpty()
                    || response.choices().get(0).message() == null
                    || response.choices().get(0).message().content() == null
                    || response.choices().get(0).message().content().isBlank()) {
                throw new AgentChatProviderException("LLM-провайдер вернул пустой ответ");
            }
            return actionParser.parse(response.choices().get(0).message().content());
        } catch (AgentChatProviderException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new AgentChatProviderException("LLM-провайдер временно недоступен", ex);
        }
    }

    private List<Map<String, String>> requestBodyMessages(Request request) {
        List<Map<String, String>> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        messages.addAll(request.messages().stream()
                .map(message -> Map.of(
                        "role", message.role().name().toLowerCase(Locale.ROOT),
                        "content", message.content()))
                .toList());
        return messages;
    }

    private record CompletionResponse(List<Choice> choices) {
    }

    private record Choice(Message message) {
    }

    private record Message(String role, String content) {
    }
}
