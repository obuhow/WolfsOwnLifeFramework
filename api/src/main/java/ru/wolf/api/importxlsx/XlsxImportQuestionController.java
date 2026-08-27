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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.importxlsx.XlsxImportQuestionService.XlsxImportQuestionResponse;

import java.util.List;

@RestController
@RequestMapping("/api/v1/import/xlsx")
@RequiredArgsConstructor
public class XlsxImportQuestionController {
    private final XlsxImportQuestionService service;

    @GetMapping("/{id}/questions")
    public ResponseEntity<List<XlsxImportQuestionResponse>> questions(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(service.questions(auth.getName(), id));
    }
}
