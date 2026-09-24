package ru.wolf.api.agentchat;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.wolf.api.note.assistant.NotesAssistantProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class HttpAgentChatAdapterTest {

    @Test
    void sends_configured_model_system_prompt_and_conversation() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NotesAssistantProperties properties = new NotesAssistantProperties();
        properties.setUrl("https://llm.example/v1");
        properties.setModel("planner-test");
        properties.setApiKey("test-key");

        server.expect(requestTo("https://llm.example/v1/chat/completions"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.model").value("planner-test"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("question"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"answer\"}}]}",
                        MediaType.APPLICATION_JSON));

        HttpAgentChatAdapter adapter = new HttpAgentChatAdapter(builder, properties);
        AgentChatPort.Response response = adapter.complete(new AgentChatPort.Request(
                "system", List.of(new AgentChatPort.Message(ChatMessage.Role.USER, "question"))));

        assertThat(response.content()).isEqualTo("answer");
        server.verify();
    }
}
