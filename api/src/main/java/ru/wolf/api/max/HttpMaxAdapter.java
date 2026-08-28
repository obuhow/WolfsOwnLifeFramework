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
 * GNU Affero General License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. if not, see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.max;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import lombok.RequiredArgsConstructor;

/**
 * Production Max transport: calls the Bot API over HTTPS (base
 * {@code platform-api2.max.ru}). Active on every profile except {@code test}
 * (where {@link FakeMaxAdapter} stands in so no real network call is made).
 *
 * <p>Endpoints used (Max Bot API):
 * <ul>
 *   <li>{@code POST /messages} — sendMessage / sendCard (card attaches an
 *       {@code inline_keyboard} with {@code callback} buttons);</li>
 *   <li>{@code POST /answers?callback_id=...} — answerCallback (stops typing);</li>
 *   <li>{@code PATCH /messages/{messageId}} — editMessage (replace the card text).</li>
 * </ul>
 *
 * The bot never initiates a conversation on its own — every call here is a direct
 * reply to an inbound update.
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
public class HttpMaxAdapter implements MaxPort {

    private final MaxProperties properties;
    @Qualifier("maxRestClient")
    private final RestClient restClient;

    private String messagesUrl() {
        return properties.getApiBase().replaceAll("/+$", "") + "/messages";
    }

    private RestClient.RequestBodySpec auth(RestClient.RequestBodySpec spec) {
        return spec.header(HttpHeaders.AUTHORIZATION, properties.getBotToken())
                .contentType(MediaType.APPLICATION_JSON);
    }

    @Override
    public void sendMessage(String chatId, String text) {
        auth(restClient.post().uri(messagesUrl()))
                .body(Map.of("recipient", Map.of("chat_id", chatId), "text", text))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void sendCard(String chatId, String text, String callbackDataAccept, String callbackDataReject) {
        Object acceptButton = MaxInlineButton.MaxButton.of("Принять", callbackDataAccept);
        Object rejectButton = MaxInlineButton.MaxButton.of("Отклонить", callbackDataReject);
        Map<String, Object> keyboard = Map.of(
                "type", "inline_keyboard",
                "payload", Map.of("buttons", List.of(List.of(acceptButton, rejectButton)))
        );
        Map<String, Object> body = Map.of(
                "recipient", Map.of("chat_id", chatId),
                "text", text,
                "attachments", List.of(keyboard)
        );
        auth(restClient.post().uri(messagesUrl()))
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void answerCallback(String callbackId) {
        auth(restClient.post().uri(messagesUrl() + "/answers?callback_id=" + callbackId))
                .body(Map.of("notification", ""))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void editMessage(String chatId, String messageId, String text) {
        auth(restClient.patch().uri(messagesUrl() + "/" + messageId))
                .body(Map.of("recipient", Map.of("chat_id", chatId), "text", text))
                .retrieve()
                .toBodilessEntity();
    }
}
