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

import ru.wolf.api.importer.dto.LlmParseRequest;
import ru.wolf.api.importer.dto.LlmParseResponse;

/**
 * Application port for the LLM call behind {@code ImportParserService}.
 *
 * <p>Distinct from {@code AssistantPort} (NotesAssistant): that port works with
 * existing Notes (transcribe/summarize/suggest); this port turns unverified
 * free text into structured entity candidates via the model's structured-output
 * mode. Both share the {@code wolf.llm.*} HTTP client configuration, but the
 * contracts must not be merged.
 */
public interface ImportParserLlmPort {

    /**
     * @return the model's raw structured-output JSON matching {@code request.jsonSchema()},
     *         or {@code null} on an empty/invalid response.
     */
    LlmParseResponse parse(LlmParseRequest request);
}
