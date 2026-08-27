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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import ru.wolf.api.importer.dto.LlmParseRequest;
import ru.wolf.api.importer.dto.LlmParseResponse;
import ru.wolf.api.importer.dto.Confidence;
import ru.wolf.api.importer.dto.EntityKind;
import ru.wolf.api.importer.dto.ParseResult;
import ru.wolf.api.importer.dto.ParsedCandidate;
import ru.wolf.api.importer.dto.ParsedField;
import ru.wolf.api.importer.dto.SlotConflict;
import ru.wolf.api.recurrence.RecurrenceService;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;

/**
 * Общий сервис разбора свободного текста в кандидаты сущностей WOLF.
 *
 * <p>Единственный источник семантики разбора для всех каналов входа
 * (чат-панель, Telegram, Max). Каналы не содержат логики разбора — только
 * доставку сообщения и отрисовку карточки подтверждения, чтобы правило
 * «час по умолчанию» и прочие правила не разошлись между каналами.
 *
 * <p>Принцип «WOLF не додумывает» применяется на уровне поля: однозначные
 * поля помечаются {@link Confidence#CONFIDENT} и применяются сразу; неоднозначные
 * (подставленные по умолчанию) помечаются {@link Confidence#NEEDS_CONFIRMATION}
 * и обязаны пройти подтверждение пользователем.
 */
@Service
@RequiredArgsConstructor
public class ImportParserService {

    /** Default duration (minutes) for an action with no explicit duration marker. */
    static final int DEFAULT_DURATION_MINUTES = 60;

    private final ImportParserLlmPort llmPort;
    private final ObjectMapper objectMapper;
    private final TimeEntryRepository timeEntryRepository;

    @Transactional(readOnly = true)
    public ParseResult parse(User user, String text) {
        if (text == null || text.isBlank()) {
            return ParseResult.unparsed("Не понял, что за дело — уточни в паре слов?");
        }

        String jsonSchema = buildJsonSchema();
        String systemPrompt = buildSystemPrompt();

        LlmParseResponse first = llmPort.parse(new LlmParseRequest(text, jsonSchema, systemPrompt));
        LlmParseResponse effective = first;
        if (!isValidJson(first)) {
            LlmParseResponse retry = llmPort.parse(new LlmParseRequest(text, jsonSchema, systemPrompt));
            if (!isValidJson(retry)) {
                return ParseResult.unparsed("Не разобрал — уточни в паре слов?");
            }
            effective = retry;
        }

        List<LlmCandidate> raw = parseCandidates(effective.content());
        if (raw.isEmpty()) {
            return ParseResult.unparsed("Не разобрал — уточни в паре слов?");
        }

        ZoneId zone = ZoneId.of(user.getTimezone());
        int year = LocalDate.now(zone).getYear();
        List<ParsedCandidate> candidates = new ArrayList<>();
        for (LlmCandidate c : raw) {
            candidates.add(applyConfidenceTable(c, year));
        }

        List<SlotConflict> conflicts = detectConflicts(user, candidates);
        return ParseResult.parsed(candidates, conflicts);
    }

    private List<LlmCandidate> parseCandidates(String content) {
        try {
            LlmCandidateList list = objectMapper.readValue(content, LlmCandidateList.class);
            return list.candidates() == null ? List.of() : list.candidates();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static boolean isValidJson(LlmParseResponse response) {
        if (response == null || response.content() == null) {
            return false;
        }
        try {
            new ObjectMapper().readTree(response.content());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Applies the confidence table verbatim:
     * <ul>
     *   <li>default duration of 60 minutes (marked NEEDS_CONFIRMATION) when no
     *       explicit duration marker is present in the text;</li>
     *   <li>current user year substituted when the date omits the year
     *       (NEEDS_CONFIRMATION — the year was inferred);</li>
     *   <li>entity kind restricted: only DELO / RECURRENCE by default; PROJECT
     *       and ROUTINE require explicit phrasing in the text;</li>
     *   <li>recurrence horizon capped at {@link RecurrenceService#MAX_HORIZON_WEEKS}
     *       (12 weeks) — never materialize 52 entries.</li>
     * </ul>
     */
    private ParsedCandidate applyConfidenceTable(LlmCandidate c, int currentYear) {
        EntityKind kind = normalizeKind(c.kind(), c.text());
        List<ParsedField> fields = new ArrayList<>();
        fields.add(ParsedField.confident("kind", kind.name()));
        if (c.title() != null && !c.title().isBlank()) {
            fields.add(ParsedField.confident("title", c.title()));
        } else {
            fields.add(ParsedField.needsConfirmation("title", ""));
        }

        // Duration: explicit marker → confident; absent → default 60 min (needs confirmation)
        if (c.durationMinutes() != null) {
            fields.add(ParsedField.confident("durationMinutes", String.valueOf(c.durationMinutes())));
        } else {
            fields.add(ParsedField.needsConfirmation("durationMinutes", String.valueOf(DEFAULT_DURATION_MINUTES)));
        }

        // Start time: explicit → confident; absent → "now" (needs confirmation)
        if (c.startAt() != null && !c.startAt().isBlank()) {
            fields.add(ParsedField.confident("startAt", c.startAt()));
        } else {
            fields.add(ParsedField.needsConfirmation("startAt", "now"));
        }

        // Year substitution for a date without an explicit year
        if (c.year() != null) {
            fields.add(ParsedField.confident("year", String.valueOf(c.year())));
        } else {
            fields.add(ParsedField.needsConfirmation("year", String.valueOf(currentYear)));
        }

        if (c.recurrenceWeekday() != null && !c.recurrenceWeekday().isBlank()) {
            fields.add(ParsedField.confident("recurrenceWeekday", c.recurrenceWeekday()));
            int horizon = c.horizonWeeks() != null
                    ? Math.min(c.horizonWeeks(), RecurrenceService.MAX_HORIZON_WEEKS)
                    : RecurrenceService.MAX_HORIZON_WEEKS;
            fields.add(ParsedField.confident("horizonWeeks",
                    String.valueOf(Math.min(horizon, RecurrenceService.MAX_HORIZON_WEEKS))));
            fields.add(ParsedField.confident("recurrenceTime", c.recurrenceTime() == null ? "" : c.recurrenceTime()));
        }

        if (c.projectRef() != null && !c.projectRef().isBlank()) {
            fields.add(ParsedField.confident("projectRef", c.projectRef()));
        }

        return new ParsedCandidate(kind, List.copyOf(fields));
    }

    private EntityKind normalizeKind(String rawKind, String text) {
        if (rawKind == null) {
            return EntityKind.DELO;
        }
        return switch (rawKind.trim().toUpperCase()) {
            case "PROJECT" -> EntityKind.PROJECT;
            case "ROUTINE" -> EntityKind.ROUTINE;
            case "RECURRENCE" -> EntityKind.RECURRENCE;
            default -> EntityKind.DELO;
        };
    }

    private List<SlotConflict> detectConflicts(User user, List<ParsedCandidate> candidates) {
        List<SlotConflict> conflicts = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            ParsedCandidate candidate = candidates.get(i);
            LocalDateTime start = slotStart(candidate);
            LocalDateTime end = slotEnd(candidate);
            if (start == null || end == null || !end.isAfter(start)) {
                continue;
            }
            List<TimeEntry> overlapping = timeEntryRepository.findOverlapping(user.getId(), start, end);
            if (!overlapping.isEmpty()) {
                conflicts.add(SlotConflict.of(i, start, end, overlapping.get(0).getId()));
            }
        }
        return conflicts;
    }

    private LocalDateTime slotStart(ParsedCandidate candidate) {
        String start = field(candidate, "startAt");
        if (start == null || "now".equals(start)) {
            return null;
        }
        return parseDateTime(start);
    }

    private LocalDateTime slotEnd(ParsedCandidate candidate) {
        LocalDateTime start = slotStart(candidate);
        if (start == null) {
            return null;
        }
        String dur = field(candidate, "durationMinutes");
        int minutes = DEFAULT_DURATION_MINUTES;
        if (dur != null) {
            try {
                minutes = Integer.parseInt(dur);
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }
        return start.plusMinutes(minutes);
    }

    private static LocalDateTime parseDateTime(String raw) {
        try {
            return LocalDateTime.parse(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private static String field(ParsedCandidate candidate, String name) {
        return candidate.fields().stream()
                .filter(f -> f.name().equals(name))
                .map(ParsedField::value)
                .findFirst()
                .orElse(null);
    }

    private String buildJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "candidates": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "kind": { "type": "string", "enum": ["DELO", "PROJECT", "ROUTINE", "RECURRENCE"] },
                          "text": { "type": "string" },
                          "title": { "type": "string" },
                          "durationMinutes": { "type": ["integer", "null"] },
                          "startAt": { "type": ["string", "null"], "description": "ISO LocalDateTime or null for 'now'" },
                          "year": { "type": ["integer", "null"], "description": "explicit year or null if omitted" },
                          "recurrenceWeekday": { "type": ["string", "null"] },
                          "recurrenceTime": { "type": ["string", "null"] },
                          "horizonWeeks": { "type": ["integer", "null"] },
                          "projectRef": { "type": ["string", "null"], "description": "project name from 'в проекте X'" }
                        },
                        "required": ["kind", "text", "title", "durationMinutes", "startAt", "year",
                                      "recurrenceWeekday", "recurrenceTime", "horizonWeeks", "projectRef"],
                        "additionalProperties": false
                      }
                    }
                  },
                  "required": ["candidates"],
                  "additionalProperties": false
                }
                """;
    }

    private String buildSystemPrompt() {
        return """
                Ты разбираешь сообщение пользователя WOLF на кандидатов сущностей.
                Правила:
                - Дело (kind=DELO) создаётся по умолчанию из любого действия.
                - Проект (kind=PROJECT) — только если в тексте явно сказано «в проекте X».
                - Рутина (kind=ROUTINE) — только если явно сказано «как рутину».
                - Повторение (kind=RECURRENCE) — если есть «каждый/каждую <день недели> в <время>».
                - durationMinutes: укажи только если в тексте есть явный маркер длительности, иначе null.
                - startAt: ISO LocalDateTime, если время названо явно, иначе null (означает «сейчас»).
                - year: укажи только если год назван явно, иначе null.
                - horizonWeeks: максимум 12, только для RECURRENCE.
                """;
    }

    // ---- LLM raw shape (matches jsonSchema) ----
    public record LlmCandidate(
            String kind,
            String text,
            String title,
            Integer durationMinutes,
            String startAt,
            Integer year,
            String recurrenceWeekday,
            String recurrenceTime,
            Integer horizonWeeks,
            String projectRef
    ) {
    }

    public record LlmCandidateList(List<LlmCandidate> candidates) {
    }
}
