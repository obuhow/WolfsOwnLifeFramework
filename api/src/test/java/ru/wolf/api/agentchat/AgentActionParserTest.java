package ru.wolf.api.agentchat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentActionParserTest {

    private final AgentActionParser parser = new AgentActionParser(new ObjectMapper());

    @Test
    void parses_reply_and_one_whitelisted_proposed_action() {
        AgentChatPort.Response response = parser.parse("""
                {"text":"Создам Дело после подтверждения.","action":{"type":"CREATE_DELO","fields":{"title":"Разобрать почту"}}}
                """);

        assertThat(response.content()).isEqualTo("Создам Дело после подтверждения.");
        assertThat(response.action()).isNotNull();
        assertThat(response.action().type()).isEqualTo(AgentAction.Type.CREATE_DELO);
        assertThat(response.action().fields()).containsEntry("title", "Разобрать почту");
    }

    @Test
    void treats_plain_provider_text_as_reply_without_action() {
        AgentChatPort.Response response = parser.parse("Обычный ответ без предложения.");

        assertThat(response.content()).isEqualTo("Обычный ответ без предложения.");
        assertThat(response.action()).isNull();
    }

    @Test
    void rejects_unknown_action_types_instead_of_persisting_them() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> parser.parse(
                        "{\"text\":\"опасно\",\"action\":{\"type\":\"RUN_SQL\",\"fields\":{}}}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
