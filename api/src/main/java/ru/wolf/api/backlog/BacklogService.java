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
 * along with this program. If not see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.backlog;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.backlog.dto.MoveRequest;
import ru.wolf.api.backlog.dto.Request;
import ru.wolf.api.backlog.dto.Response;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BacklogService {

    private final BacklogItemRepository items;
    private final DeloRepository delos;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public List<Response> list(String username, String scope, String period) {
        User user = current(username);
        BacklogItem.Scope parsedScope = BacklogItem.Scope.valueOf(scope.toUpperCase());
        return items.findPeriod(user, parsedScope, period).stream().map(this::response).toList();
    }

    @Transactional
    public ResponseEntity<Response> create(String username, Request request) {
        User user = current(username);
        Delo delo = delos.findByUserAndId(user, request.deloId()).orElseThrow();
        if (items.findByUserAndDeloIdAndScopeAndPeriodId(user, delo.getId(), request.scope(), request.period()).isPresent()) {
            return ResponseEntity.status(409).build();
        }
        BacklogItem saved = items.save(BacklogItem.builder()
                .user(user).delo(delo).scope(request.scope()).periodId(request.period())
                .plannedHours(request.plannedHours()).position(request.position() == null ? 0 : request.position()).build());
        return ResponseEntity.status(HttpStatus.CREATED).body(response(saved));
    }

    @Transactional
    public Response update(String username, Long id, Request request) {
        BacklogItem item = owned(username, id);
        if (request.plannedHours() != null) item.setPlannedHours(request.plannedHours());
        if (request.position() != null) item.setPosition(request.position());
        return response(items.save(item));
    }

    @Transactional
    public void delete(String username, Long id) {
        items.delete(owned(username, id));
    }

    @Transactional
    public Response moveToWeek(String username, Long id, MoveRequest request) {
        BacklogItem month = owned(username, id);
        if (month.getScope() != BacklogItem.Scope.MONTH) throw new IllegalArgumentException("Переносить можно только месячный бэклог");
        User user = current(username);
        if (items.findByUserAndDeloIdAndScopeAndPeriodId(user, month.getDelo().getId(), BacklogItem.Scope.WEEK, request.week()).isEmpty()) {
            items.save(BacklogItem.builder().user(user).delo(month.getDelo()).scope(BacklogItem.Scope.WEEK)
                    .periodId(request.week()).plannedHours(month.getPlannedHours()).position(0).build());
        }
        month.setMovedToWeek(request.week());
        return response(items.save(month));
    }

    private BacklogItem owned(String username, Long id) {
        return items.findByUserAndId(current(username), id).orElseThrow();
    }

    private User current(String username) {
        return users.findByUsername(username).orElseThrow();
    }

    private Response response(BacklogItem item) {
        return new Response(item.getId(), item.getDelo().getId(), item.getDelo().getTitle(),
                item.getScope().name().toLowerCase(), item.getPeriodId(), item.getPlannedHours(),
                item.getPosition(), item.getMovedToWeek());
    }
}
