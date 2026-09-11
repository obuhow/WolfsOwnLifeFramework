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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class XlsxImportQuestionService {
    private final UserRepository users;
    private final XlsxImportRunRepository runs;
    private final XlsxImportQuestionRepository questions;

    public List<XlsxImportQuestionResponse> questions(String username, Long id) {
        var user = users.findByUsername(username).orElseThrow();
        runs.findByUserAndId(user, id).orElseThrow();
        Map<String, XlsxImportQuestionResponse> unique = new LinkedHashMap<>();
        for (XlsxImportQuestion question : questions.findByImportRunIdAndResolvedFalseOrderByStartAtAsc(id)) {
            unique.putIfAbsent(ActivityTextNormalizer.key(question.getActivityText()),
                    new XlsxImportQuestionResponse(question.getId(), question.getActivityText(),
                            question.getSheetName(), question.getStartAt()));
        }
        return List.copyOf(unique.values());
    }

    public record XlsxImportQuestionResponse(Long id, String activityText, String sheetName, java.time.LocalDateTime startAt) {}
}
