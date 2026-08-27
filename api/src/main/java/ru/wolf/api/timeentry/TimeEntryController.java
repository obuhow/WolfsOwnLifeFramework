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
package ru.wolf.api.timeentry;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.wolf.api.timeentry.dto.ConfirmAllRequest;
import ru.wolf.api.timeentry.dto.ConfirmAllResponse;
import ru.wolf.api.timeentry.dto.ConfirmOneRequest;
import ru.wolf.api.timeentry.dto.EnsureSleepRequest;
import ru.wolf.api.timeentry.dto.EnsureSleepResponse;
import ru.wolf.api.timeentry.dto.GridClickRequest;
import ru.wolf.api.timeentry.dto.GridClickResponse;
import ru.wolf.api.timeentry.dto.PutTimeEntryRequest;
import ru.wolf.api.timeentry.dto.TimeEntryResponse;
import ru.wolf.api.timeentry.dto.TodayResponse;
import ru.wolf.api.timeentry.dto.WeekResponse;

import java.util.List;

@RestController
@RequestMapping("/api/v1/time-entries")
@RequiredArgsConstructor
public class TimeEntryController {

    private final TimeEntryService service;

    @GetMapping
    public ResponseEntity<List<TimeEntryResponse>> listRange(
            Authentication authentication,
            @RequestParam("from") String from,
            @RequestParam("to") String to
    ) {
        return service.listRange(authentication.getName(), from, to);
    }

    @GetMapping("/today")
    public ResponseEntity<TodayResponse> today(
            Authentication authentication,
            @RequestParam(value = "date", required = false) String date
    ) {
        return service.today(authentication.getName(), date);
    }

    @GetMapping("/week")
    public ResponseEntity<WeekResponse> week(
            Authentication authentication,
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "isoYear", required = false) Integer isoYear,
            @RequestParam(value = "isoWeek", required = false) Integer isoWeek
    ) {
        return service.week(authentication.getName(), date, isoYear, isoWeek);
    }

    @PutMapping
    public ResponseEntity<TimeEntryResponse> putEntry(
            Authentication authentication,
            @Valid @RequestBody PutTimeEntryRequest request
    ) {
        return service.putEntry(authentication.getName(), request);
    }

    @PostMapping("/grid-click")
    public ResponseEntity<GridClickResponse> gridClick(
            Authentication authentication,
            @Valid @RequestBody GridClickRequest request
    ) {
        return service.gridClick(authentication.getName(), request);
    }

    @DeleteMapping
    public ResponseEntity<Void> clearEntry(
            Authentication authentication,
            @RequestParam("startAt") String startAt
    ) {
        return service.clearEntry(authentication.getName(), startAt);
    }

    @PostMapping("/confirm")
    public ResponseEntity<TimeEntryResponse> confirmOne(
            Authentication authentication,
            @Valid @RequestBody ConfirmOneRequest request
    ) {
        return service.confirmOne(authentication.getName(), request);
    }

    @PostMapping("/confirm-all")
    public ResponseEntity<ConfirmAllResponse> confirmAll(
            Authentication authentication,
            @Valid @RequestBody ConfirmAllRequest request
    ) {
        return service.confirmAll(authentication.getName(), request);
    }

    @PostMapping("/ensure-sleep")
    public ResponseEntity<EnsureSleepResponse> ensureSleep(
            Authentication authentication,
            @Valid @RequestBody EnsureSleepRequest request
    ) {
        return service.ensureSleep(authentication.getName(), request);
    }
}
