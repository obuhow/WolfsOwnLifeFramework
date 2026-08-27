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
package ru.wolf.api.importxlsx;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.wolf.api.user.UserRepository;
import java.util.List;

@Service
@RequiredArgsConstructor
public class XlsxImportQuestionService {
    private final UserRepository users;
    private final XlsxImportRunRepository runs;
    private final XlsxImportQuestionRepository questions;

    public List<XlsxImportQuestionResponse> questions(String username, Long id) {
        var user = users.findByUsername(username).orElseThrow();
        runs.findByUserAndId(user, id).orElseThrow();
        return questions.findByImportRunIdAndResolvedFalseOrderByStartAtAsc(id).stream()
                .map(q -> new XlsxImportQuestionResponse(q.getId(), q.getActivityText(), q.getSheetName(), q.getStartAt()))
                .toList();
    }

    public record XlsxImportQuestionResponse(Long id, String activityText, String sheetName, java.time.LocalDateTime startAt) {}
}
