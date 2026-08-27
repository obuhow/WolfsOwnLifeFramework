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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import ru.wolf.api.note.assistant.dto.ResumeResponse;

class AssistantControllerUnitTest {

    @Test
    void resume_throws_llm_disabled_when_disabled() {
        NotesAssistantProperties properties = mock(NotesAssistantProperties.class);
        when(properties.isEnabled()).thenReturn(false);
        ProjectResumeController controller = new ProjectResumeController(properties, mock(NotesAssistantService.class));

        assertThatThrownBy(() -> controller.resume(mock(Authentication.class), 1L, 10))
                .isInstanceOf(LlmDisabledException.class)
                .hasMessage("LLM-функции отключены: установите wolf.llm.enabled=true");
    }

    @Test
    void resume_rejects_out_of_range_limit() {
        NotesAssistantProperties properties = mock(NotesAssistantProperties.class);
        when(properties.isEnabled()).thenReturn(true);
        ProjectResumeController controller = new ProjectResumeController(properties, mock(NotesAssistantService.class));

        assertThatThrownBy(() -> controller.resume(mock(Authentication.class), 1L, 200))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Параметр limit должен быть от 1 до 100");
    }

    @Test
    void resume_delegates_to_service_when_enabled_and_valid() {
        NotesAssistantProperties properties = mock(NotesAssistantProperties.class);
        when(properties.isEnabled()).thenReturn(true);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("admin");
        NotesAssistantService service = mock(NotesAssistantService.class);
        ResumeResponse expected = new ResumeResponse(1L, "WOLF", java.util.List.of(2L, 3L), "summary");
        when(service.resume("admin", 1L, 10)).thenReturn(expected);
        ProjectResumeController controller = new ProjectResumeController(properties, service);

        ResponseEntity<ResumeResponse> response = controller.resume(authentication, 1L, 10);
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(expected);
    }
}
