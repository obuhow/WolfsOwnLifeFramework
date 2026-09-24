package ru.wolf.api.agentchat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/** Deterministic test adapter; no network call is made under the test profile. */
@Component
@Profile("test")
public class FakeAgentChatAdapter implements AgentChatPort {

    private final AtomicReference<String> response = new AtomicReference<>("Тестовый ответ агента");
    private final AtomicReference<String> failure = new AtomicReference<>();
    private final AtomicReference<AgentAction> action = new AtomicReference<>();
    private final AtomicReference<Request> lastRequest = new AtomicReference<>();
    private final AgentActionParser responseParser = new AgentActionParser(new ObjectMapper());

    @Override
    public Response complete(Request request) {
        lastRequest.set(request);
        String error = failure.get();
        if (error != null) {
            throw new AgentChatProviderException(error);
        }
        AgentAction proposed = action.get();
        return proposed == null ? responseParser.parse(response.get()) : new Response(response.get(), proposed);
    }

    public void setResponse(String value) {
        response.set(value);
        failure.set(null);
    }

    public void failWith(String message) {
        failure.set(message);
    }

    public void setAction(AgentAction value) {
        action.set(value);
    }

    public Request lastRequest() {
        return lastRequest.get();
    }

    public void reset() {
        response.set("Тестовый ответ агента");
        failure.set(null);
        action.set(null);
        lastRequest.set(null);
    }
}
