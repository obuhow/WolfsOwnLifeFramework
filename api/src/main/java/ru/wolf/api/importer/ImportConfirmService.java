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

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.delo.DeloService;
import ru.wolf.api.delo.dto.CreateDeloRequest;
import ru.wolf.api.importer.dto.ConfirmCandidate;
import ru.wolf.api.importer.dto.ConfirmImportRequest;
import ru.wolf.api.importer.dto.ConfirmImportResponse;
import ru.wolf.api.importer.dto.CreatedEntity;
import ru.wolf.api.importer.dto.EntityKind;
import ru.wolf.api.importer.dto.ParseResult;
import ru.wolf.api.importer.dto.ParsedField;
import ru.wolf.api.lifearea.LifeArea;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.project.ProjectService;
import ru.wolf.api.project.dto.CreateProjectRequest;
import ru.wolf.api.recurrence.RecurrenceService;
import ru.wolf.api.routine.RoutineService;
import ru.wolf.api.routine.dto.RoutineRequest;
import ru.wolf.api.routine.dto.RoutineResponse;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.timeentry.TimeEntry.Status;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

/**
 * Turns confirmed chat-panel candidates into real WOLF entities.
 *
 * <p>This is the single write path for the import channel: it delegates to the
 * existing feature services ({@link DeloService}, {@link ProjectService},
 * {@link RoutineService}, {@link RecurrenceService}) so their business rules stay
 * authoritative, and only adds the channel-specific glue (bot LifeArea, parallel
 * time-entry slot for an occupied window). The calling channel decides which
 * candidates to confirm; this service performs the writes.
 *
 * <p>Occupied time slots are always written as a parallel (CREATE_OVER) entry —
 * matching WOLF's default {@code PARALLEL_SLOTS} capture mode, where overlapping
 * intervals are allowed rather than rejected.
 */
@Service
@RequiredArgsConstructor
public class ImportConfirmService {

    static final String BOT_LIFE_AREA = "Импорт";

    private final UserRepository userRepository;
    private final DeloRepository deloRepository;
    private final DeloService deloService;
    private final ProjectService projectService;
    private final RoutineService routineService;
    private final RecurrenceService recurrenceService;
    private final LifeAreaRepository lifeAreaRepository;
    private final TimeEntryRepository timeEntryRepository;

    @Transactional
    public ConfirmImportResponse confirm(String username, ConfirmImportRequest request) {
        User user = currentUser(username);
        List<CreatedEntity> created = new ArrayList<>();
        for (ConfirmCandidate candidate : request.candidates()) {
            CreatedEntity entity = switch (candidate.kind()) {
                case DELO -> createDelo(user, candidate);
                case PROJECT -> createProject(user, candidate);
                case ROUTINE -> createRoutine(user, candidate);
                case RECURRENCE -> createRecurrence(user, candidate);
            };
            if (entity != null) {
                created.add(entity);
            }
        }
        return new ConfirmImportResponse(created);
    }

    /** Mirror of {@link ParseResult#unparsed} — kept here so the controller has one import service. */
    public ParseResult unparsed(String clarificationQuestion) {
        return ParseResult.unparsed(clarificationQuestion);
    }

    private CreatedEntity createDelo(User user, ConfirmCandidate candidate) {
        String title = field(candidate, "title");
        if (title == null || title.isBlank()) {
            return null;
        }
        Long id = deloService.createDelo(user.getUsername(),
                new CreateDeloRequest(title.trim(), null, Delo.ExecutionMode.SELF, null, null)).id();

        // Link to an explicitly named project ("в проекте X") when present.
        String projectRef = field(candidate, "projectRef");
        if (projectRef != null && !projectRef.isBlank()) {
            projectService.listProjects(user.getUsername(), null).stream()
                    .filter(p -> p.title().equalsIgnoreCase(projectRef.trim()))
                    .findFirst()
                    .ifPresent(p -> deloService.linkProject(user.getUsername(), id, p.id()));
        }

        // A concrete start time (not "now") → place a parallel time-entry slot.
        String startAt = field(candidate, "startAt");
        if (startAt != null && !startAt.isBlank() && !"now".equals(startAt)) {
            placeParallelSlot(user, id, startAt, durationMinutes(candidate));
        }
        return new CreatedEntity(EntityKind.DELO, id, title.trim(), "delo", "/delos/" + id);
    }

    private CreatedEntity createProject(User user, ConfirmCandidate candidate) {
        String title = field(candidate, "title");
        if (title == null || title.isBlank()) {
            return null;
        }
        LifeArea area = botLifeArea(user);
        Long id = projectService.createProject(user.getUsername(),
                new CreateProjectRequest(area.getId(), title.trim())).id();
        return new CreatedEntity(EntityKind.PROJECT, id, title.trim(), "project", "/projects/" + id);
    }

    private CreatedEntity createRoutine(User user, ConfirmCandidate candidate) {
        String title = field(candidate, "title");
        if (title == null || title.isBlank()) {
            return null;
        }
        ResponseEntity<RoutineResponse> response = routineService.create(user.getUsername(),
                new RoutineRequest(title.trim(), null, BigDecimal.ZERO, null, null));
        Long id = response.getBody().id();
        return new CreatedEntity(EntityKind.ROUTINE, id, title.trim(), "routine", "/routines");
    }

    private CreatedEntity createRecurrence(User user, ConfirmCandidate candidate) {
        String title = field(candidate, "title");
        if (title == null || title.isBlank()) {
            return null;
        }
        Long deloId = deloService.createDelo(user.getUsername(),
                new CreateDeloRequest(title.trim(), null, Delo.ExecutionMode.SELF, null, null)).id();

        String weekdayRaw = field(candidate, "recurrenceWeekday");
        String timeRaw = field(candidate, "recurrenceTime");
        if (weekdayRaw != null && !weekdayRaw.isBlank() && timeRaw != null && !timeRaw.isBlank()) {
            DayOfWeek weekday = DayOfWeek.valueOf(weekdayRaw.trim().toUpperCase());
            LocalTime start = LocalTime.parse(timeRaw.trim());
            LocalTime end = start.plusMinutes(15);
            int horizon = parseHorizon(field(candidate, "horizonWeeks"));
            recurrenceService.apply(user, deloId, new RecurrenceService.ApplyCommand(
                    List.of(weekday), start, end, horizon, null));
        }
        return new CreatedEntity(EntityKind.RECURRENCE, deloId, title.trim(), "recurrence", "/delos/" + deloId);
    }

    private void placeParallelSlot(User user, Long deloId, String startAtRaw, int durationMinutes) {
        LocalDateTime start;
        try {
            start = LocalDateTime.parse(startAtRaw.trim());
        } catch (Exception e) {
            return; // unparseable slot — skip the time entry, the Delo still exists
        }
        start = floorTo15(start);
        int minutes = durationMinutes > 0 ? durationMinutes : ImportParserService.DEFAULT_DURATION_MINUTES;
        LocalDateTime end = floorTo15(start.plusMinutes(minutes));
        if (!end.isAfter(start)) {
            end = start.plusMinutes(15);
        }
        Status status = statusFor(user, start);
        timeEntryRepository.save(TimeEntry.builder()
                .user(user)
                .delo(deloRepository.findById(deloId).orElse(null))
                .startAt(start)
                .endAt(end)
                .status(status)
                .build());
    }

    private Status statusFor(User user, LocalDateTime start) {
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDateTime now = LocalDateTime.now(zone);
        return start.isAfter(now) ? Status.PLANNED : Status.DONE;
    }

    private int parseHorizon(String raw) {
        if (raw == null || raw.isBlank()) {
            return RecurrenceService.MAX_HORIZON_WEEKS;
        }
        try {
            return Math.min(Integer.parseInt(raw.trim()), RecurrenceService.MAX_HORIZON_WEEKS);
        } catch (NumberFormatException e) {
            return RecurrenceService.MAX_HORIZON_WEEKS;
        }
    }

    private int durationMinutes(ConfirmCandidate candidate) {
        String raw = field(candidate, "durationMinutes");
        if (raw == null || raw.isBlank()) {
            return ImportParserService.DEFAULT_DURATION_MINUTES;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return ImportParserService.DEFAULT_DURATION_MINUTES;
        }
    }

    private static LocalDateTime floorTo15(LocalDateTime ldt) {
        int minute = ldt.getMinute() - (ldt.getMinute() % 15);
        return ldt.withMinute(minute).withSecond(0).withNano(0).truncatedTo(ChronoUnit.MINUTES);
    }

    private LifeArea botLifeArea(User user) {
        return lifeAreaRepository.findByUserAndNameIgnoreCase(user, BOT_LIFE_AREA)
                .orElseGet(() -> lifeAreaRepository.save(LifeArea.builder()
                        .user(user)
                        .name(BOT_LIFE_AREA)
                        .sortOrder(lifeAreaRepository.findMaxSortOrderByUser(user) + 1)
                        .build()));
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private Map<String, String> fieldMap(ConfirmCandidate candidate) {
        return candidate.fields().stream()
                .collect(Collectors.toMap(ParsedField::name, ParsedField::value, (a, b) -> b));
    }

    private String field(ConfirmCandidate candidate, String name) {
        return fieldMap(candidate).get(name);
    }
}
