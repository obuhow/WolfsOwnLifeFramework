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
 * along with this program. if not, see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import lombok.RequiredArgsConstructor;
import ru.wolf.api.telegram.dto.TelegramMessage;

/**
 * Production Telegram transport: calls the Bot API over HTTPS. Active on every
 * profile except {@code test} (where {@link FakeTelegramAdapter} stands in so
 * no real network call is made). The bot never initiates a conversation on its
 * own — every call here is a direct reply to an inbound update.
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
public class HttpTelegramAdapter implements TelegramPort {

    private final TelegramProperties properties;
    @Qualifier("telegramRestClient")
    private final RestClient restClient;

    private String url(String method) {
        return properties.getApiBase().replaceAll("/+$", "")
                + "/bot" + properties.getBotToken() + "/" + method;
    }

    @Override
    public void sendMessage(String chatId, String text) {
        restClient.post()
                .uri(url("sendMessage"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("chat_id", chatId, "text", text))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void sendCard(String chatId, String text, String callbackDataAccept, String callbackDataReject) {
        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", text,
                "reply_markup", Map.of("inline_keyboard", List.of(List.of(
                        InlineButton.of("Принять", callbackDataAccept),
                        InlineButton.of("Отклонить", callbackDataReject))))
        );
        restClient.post()
                .uri(url("sendMessage"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(TelegramMessage.class);
    }

    @Override
    public void answerCallback(String callbackQueryId) {
        restClient.post()
                .uri(url("answerCallbackQuery"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("callback_query_id", callbackQueryId))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void editMessage(String chatId, long messageId, String text) {
        restClient.post()
                .uri(url("editMessageText"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("chat_id", chatId, "message_id", messageId, "text", text))
                .retrieve()
                .toBodilessEntity();
    }

    /** Discouraged reflective-access shim kept private; JSON shape only. */
    private record CallbackAck(@JsonProperty("ok") boolean ok) {
    }
}
