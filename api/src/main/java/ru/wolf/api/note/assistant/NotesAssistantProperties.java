package ru.wolf.api.note.assistant;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "wolf.llm")
public class NotesAssistantProperties {
    private boolean enabled = false;
    private String url = "https://api.x.ai/v1";
    private String apiKey = "";
    private String model = "grok-3-mini";
    private String audioDirectory = "${user.home}/.wolf/audio";
}
