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
package ru.wolf.api.agentchat;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.agentchat.dto.AgentChatRequest;
import ru.wolf.api.agentchat.dto.AgentActionApplyResponse;
import ru.wolf.api.agentchat.dto.AgentAvailabilityResponse;
import ru.wolf.api.agentchat.dto.AgentChatResponse;
import ru.wolf.api.agentchat.dto.ChatMessageRequest;
import ru.wolf.api.agentchat.dto.ChatMessageResponse;
import ru.wolf.api.agentchat.dto.ChatSessionResponse;
import ru.wolf.api.agentchat.dto.ProposedActionResponse;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agent-chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final AgentChatService agentChatService;
    private final AgentActionService actionService;

    @GetMapping("/status")
    public ResponseEntity<AgentAvailabilityResponse> status() {
        return ResponseEntity.ok(agentChatService.availability());
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSessionResponse>> listSessions(Authentication authentication) {
        return ResponseEntity.ok(chatService.listSessions(authentication.getName()));
    }

    @PostMapping("/sessions")
    public ResponseEntity<ChatSessionResponse> createSession(Authentication authentication) {
        return ResponseEntity.ok(chatService.createSession(authentication.getName()));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ChatSessionResponse> getSession(
            Authentication authentication, @PathVariable Long sessionId) {
        return ResponseEntity.ok(chatService.getSession(authentication.getName(), sessionId));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> listMessages(
            Authentication authentication,
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ResponseEntity.ok(chatService.listMessages(
                authentication.getName(), sessionId, page, limit));
    }

    @GetMapping("/sessions/{sessionId}/actions")
    public ResponseEntity<List<ProposedActionResponse>> listActions(
            Authentication authentication, @PathVariable Long sessionId) {
        return ResponseEntity.ok(actionService.list(authentication.getName(), sessionId));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<ChatMessageResponse> appendMessage(
            Authentication authentication,
            @PathVariable Long sessionId,
            @Valid @RequestBody ChatMessageRequest request
    ) {
        return ResponseEntity.ok(chatService.appendMessage(
                authentication.getName(), sessionId, request));
    }

    /** Explicit agent route; the messages route remains the history API. */
    @PostMapping("/sessions/{sessionId}/chat")
    public ResponseEntity<AgentChatResponse> complete(
            Authentication authentication,
            @PathVariable Long sessionId,
            @Valid @RequestBody AgentChatRequest request
    ) {
        return ResponseEntity.ok(agentChatService.complete(
                authentication.getName(), sessionId, request.content()));
    }

    @PostMapping("/sessions/{sessionId}/actions/{proposalId}/confirm")
    public ResponseEntity<AgentActionApplyResponse> confirmAction(
            Authentication authentication,
            @PathVariable Long sessionId,
            @PathVariable Long proposalId
    ) {
        return ResponseEntity.ok(actionService.apply(authentication.getName(), sessionId, proposalId));
    }

    @PostMapping("/sessions/{sessionId}/actions/{proposalId}/reject")
    public ResponseEntity<AgentActionApplyResponse> rejectAction(
            Authentication authentication,
            @PathVariable Long sessionId,
            @PathVariable Long proposalId
    ) {
        return ResponseEntity.ok(actionService.reject(authentication.getName(), sessionId, proposalId));
    }
}
