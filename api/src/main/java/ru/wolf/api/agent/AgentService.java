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
package ru.wolf.api.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.wolf.api.agent.dto.AgentRunResponse;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentJob agentJob;
    private final UserRepository userRepository;

    public AgentRunResponse run(String username) {
        User user = currentUser(username);
        return AgentRunResponse.from(agentJob.runForUser(user));
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }
}
