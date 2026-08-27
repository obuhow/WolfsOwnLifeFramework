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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ru.wolf.api.importer.dto.Confidence;
import ru.wolf.api.importer.dto.EntityKind;
import ru.wolf.api.importer.dto.LlmParseRequest;
import ru.wolf.api.importer.dto.LlmParseResponse;
import ru.wolf.api.importer.dto.ParseResult;
import ru.wolf.api.importer.dto.ParsedCandidate;
import ru.wolf.api.importer.dto.ParsedField;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;

@ExtendWith(MockitoExtension.class)
class ImportParserServiceTest {

    @Mock
    private ImportParserLlmPort llmPort;
    @Mock
    private TimeEntryRepository timeEntryRepository;

    private ImportParserService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new ImportParserService(llmPort, new ObjectMapper(), timeEntryRepository);
        user = User.builder().id(1L).username("alice").timezone("Europe/Moscow").build();
    }

    private static String candidatesJson(Map<String, Object>... candidates) {
        StringBuilder sb = new StringBuilder("{\"candidates\":[");
        for (int i = 0; i < candidates.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{");
            boolean first = true;
            for (var e : candidates[i].entrySet()) {
                if (!first) {
                    sb.append(",");
                }
                sb.append("\"").append(e.getKey()).append("\":");
                Object v = e.getValue();
                if (v == null) {
                    sb.append("null");
                } else if (v instanceof Number n) {
                    sb.append(n);
                } else {
                    sb.append("\"").append(v).append("\"");
                }
                first = false;
            }
            sb.append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static Map<String, Object> cand(String kind, String title, Integer durationMinutes,
                                            String startAt, Integer year, String weekday,
                                            String time, Integer horizon, String projectRef) {
        Map<String, Object> m = new HashMap<>();
        m.put("kind", kind);
        m.put("text", title);
        m.put("title", title);
        m.put("durationMinutes", durationMinutes);
        m.put("startAt", startAt);
        m.put("year", year);
        m.put("recurrenceWeekday", weekday);
        m.put("recurrenceTime", time);
        m.put("horizonWeeks", horizon);
        m.put("projectRef", projectRef);
        return m;
    }

    private static ParsedField field(ParsedCandidate c, String name) {
        return c.fields().stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void blankText_degradesToUnparsed() {
        ParseResult result = service.parse(user, "   ");
        assertThat(result.unparsed()).isTrue();
        assertThat(result.candidates()).isEmpty();
        assertThat(result.clarificationQuestion()).isNotBlank();
    }

    @Test
    void explicitDuration_isConfident() {
        String json = candidatesJson(cand("DELO", "тренировка", 90, null, null, null, null, null, null));
        when(llmPort.parse(any(LlmParseRequest.class))).thenReturn(new LlmParseResponse(json));

        ParseResult result = service.parse(user, "после тренировки, часа полтора");
        assertThat(result.unparsed()).isFalse();
        ParsedCandidate candidate = result.candidates().get(0);
        assertThat(candidate.kind()).isEqualTo(EntityKind.DELO);
        assertThat(field(candidate, "durationMinutes").value()).isEqualTo("90");
        assertThat(field(candidate, "durationMinutes").confidence()).isEqualTo(Confidence.CONFIDENT);
    }

    @Test
    void missingDuration_getsDefault60_needsConfirmation() {
        String json = candidatesJson(cand("DELO", "душ", null, null, null, null, null, null, null));
        when(llmPort.parse(any(LlmParseRequest.class))).thenReturn(new LlmParseResponse(json));

        ParseResult result = service.parse(user, "потом душ");
        ParsedCandidate candidate = result.candidates().get(0);
        assertThat(field(candidate, "durationMinutes").value()).isEqualTo("60");
        assertThat(field(candidate, "durationMinutes").confidence()).isEqualTo(Confidence.NEEDS_CONFIRMATION);
    }

    @Test
    void projectOnlyWhenExplicit() {
        String json = candidatesJson(
                cand("DELO", "звонок", 30, null, null, null, null, null, null),
                cand("PROJECT", "Ремонт", null, null, null, null, null, null, "Ремонт"));
        when(llmPort.parse(any(LlmParseRequest.class))).thenReturn(new LlmParseResponse(json));

        ParseResult result = service.parse(user, "звонок и в проекте Ремонт");
        assertThat(result.candidates()).hasSize(2);
        assertThat(result.candidates().get(0).kind()).isEqualTo(EntityKind.DELO);
        assertThat(result.candidates().get(1).kind()).isEqualTo(EntityKind.PROJECT);
    }

    @Test
    void invalidJson_retriesOnce_thenUnparsed() {
        when(llmPort.parse(any(LlmParseRequest.class)))
                .thenReturn(new LlmParseResponse("not json at all"),
                        new LlmParseResponse("still not json"));

        ParseResult result = service.parse(user, "какой-то текст");
        assertThat(result.unparsed()).isTrue();
        verify(llmPort, times(2)).parse(any(LlmParseRequest.class));
    }

    @Test
    void recurrenceHorizon_cappedAtMax() {
        String json = candidatesJson(
                cand("RECURRENCE", "английский", 60, null, null, "FRIDAY", "19:00", 52, null));
        when(llmPort.parse(any(LlmParseRequest.class))).thenReturn(new LlmParseResponse(json));

        ParseResult result = service.parse(user, "каждую пятницу в 19:00 английский");
        ParsedCandidate candidate = result.candidates().get(0);
        assertThat(candidate.kind()).isEqualTo(EntityKind.RECURRENCE);
        // horizon 52 requested but capped at RecurrenceService.MAX_HORIZON_WEEKS (12)
        assertThat(field(candidate, "horizonWeeks").value()).isEqualTo("12");
    }
}
