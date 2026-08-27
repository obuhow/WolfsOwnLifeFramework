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

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import ru.wolf.api.importer.dto.LlmParseRequest;
import ru.wolf.api.importer.dto.LlmParseResponse;

/**
 * Test double for {@link ImportParserLlmPort}. By default returns
 * {@code null} (empty model answer); tests set the JSON payload to exercise
 * the confidence-table and retry logic without a real LLM.
 */
@Profile("test")
@Component
public class FakeImportParserLlmAdapter implements ImportParserLlmPort {

    private final AtomicReference<String> response = new AtomicReference<>(null);
    private final AtomicReference<Integer> invocationCount = new AtomicReference<>(0);

    @Override
    public LlmParseResponse parse(LlmParseRequest request) {
        invocationCount.updateAndGet(c -> c + 1);
        return new LlmParseResponse(response.get());
    }

    public void setResponse(String jsonContent) {
        response.set(jsonContent);
    }

    public void setInvalidResponse() {
        response.set("this is not json at all");
    }

    public void reset() {
        response.set(null);
        invocationCount.set(0);
    }

    public int invocationCount() {
        return invocationCount.get();
    }
}
