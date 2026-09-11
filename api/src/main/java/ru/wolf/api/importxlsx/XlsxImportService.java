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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

/**
 * Run-level operations on an XLSX import: reading a run's summary and resolving the questions it
 * raised.
 *
 * <p>Release 1.4 ticket 02 moved the actual import out of here: the file is parsed by
 * {@link XlsxScheduleGridParser} and driven by {@link XlsxSchedulePreviewService} (preview → apply).
 * The previous {@code upload} method — which parsed with its own sheet-name/A–G/00:00 assumptions
 * and wrote Записи времени the moment a file arrived — was removed with it, because none of those
 * assumptions hold for the owner's real {@code Расписание.xlsx} and it silently invented a default
 * week when the sheet name carried no date. See {@code .scratch/xlsx-schedule-import/spec.md}
 * («Совместимость с существующим XLSX-импортом»): no other XLSX format is known in this repository,
 * so the parser was replaced rather than duplicated.
 */
@Service
public class XlsxImportService {
    private final UserRepository users;
    private final DeloRepository delos;
    private final TimeEntryRepository entries;
    private final ActivityMappingRepository mappings;
    private final XlsxImportRunRepository runs;
    private final XlsxImportQuestionRepository questions;

    public XlsxImportService(UserRepository users, DeloRepository delos, TimeEntryRepository entries,
                                ActivityMappingRepository mappings, XlsxImportRunRepository runs,
                                XlsxImportQuestionRepository questions) {
        this.users = users; this.delos = delos; this.entries = entries; this.mappings = mappings; this.runs = runs; this.questions = questions;
    }

    @Transactional(readOnly = true)
    public ImportResponse get(String username, Long id) {
        User user = current(username);
        XlsxImportRun run = runs.findByUserAndId(user, id).orElseThrow();
        return toResponse(run);
    }

    /**
     * Teaches the dictionary that an activity belongs to a Дело, then flips the Записи времени that
     * were parked on that activity from {@code UNKNOWN} to {@code DONE}.
     */
    @Transactional
    public ImportResponse resolve(String username, Long id, ResolveRequest request) {
        User user = current(username);
        XlsxImportRun run = runs.findByUserAndId(user, id).orElseThrow();
        Delo delo;
        if (request.deloId() != null) {
            delo = delos.findByUserAndId(user, request.deloId()).orElseThrow();
        } else {
            String title = request.createDelo().title();
            if (SupportingActivities.isSleep(title)) {
                // Т-1: «Сон» (any case) reuses the existing Дело of the «Ночные часы» mechanism
                // instead of creating a duplicate — even when the UI asked to "create new".
                Delo sleep = delos.findFirstByUserAndTitleIgnoreCaseOrderByIdAsc(user, SupportingActivities.SLEEP_DELO_TITLE)
                        .orElseGet(() -> delos.save(Delo.builder()
                                .user(user)
                                .title(SupportingActivities.SLEEP_DELO_TITLE)
                                .supporting(true)
                                .build()));
                // The «Ночные часы» Дело may predate this flag; mark it supporting on first use.
                if (!sleep.isSupporting()) {
                    sleep.setSupporting(true);
                    sleep = delos.save(sleep);
                }
                delo = sleep;
            } else {
                Boolean requested = request.createDelo().supporting();
                // Default class-1 activities (Сон, Еда, В дороге, …) to the supporting flag unless
                // the user explicitly overrode it; the flag is exposed in the Дело API responses.
                boolean supporting = requested != null ? requested : SupportingActivities.isSupporting(title);
                delo = delos.save(Delo.builder().user(user).title(title).supporting(supporting).build());
            }
        }
        ActivityMapping mapping = mappings.findByUserAndNormalizedActivityText(user, request.activityText())
                .orElseGet(() -> mappings.save(ActivityMapping.builder()
                        .user(user)
                        .activityText(ActivityTextNormalizer.normalize(request.activityText()))
                        .delo(delo)
                        .build()));
        for (XlsxImportQuestion q : questions.findByImportRunIdAndResolvedFalseOrderByStartAtAsc(id)) {
            if (ActivityTextNormalizer.key(q.getActivityText()).equals(ActivityTextNormalizer.key(request.activityText()))) {
                entries.findByUserIdAndStartAt(user.getId(), q.getStartAt()).ifPresent(e -> {
                    e.setDelo(mapping.getDelo());
                    e.setStatus(TimeEntry.Status.DONE);
                    entries.save(e);
                });
                q.setResolved(true);
                questions.save(q);
            }
        }
        run.setPendingQuestions(unresolvedActivityCount(id));
        run.setStatus(run.getPendingQuestions() == 0 ? XlsxImportRun.Status.DONE : XlsxImportRun.Status.PAUSED);
        runs.save(run);
        return toResponse(run);
    }

    private int unresolvedActivityCount(Long importRunId) {
        return (int) questions.findByImportRunIdAndResolvedFalseOrderByStartAtAsc(importRunId).stream()
                .map(question -> ActivityTextNormalizer.key(question.getActivityText()))
                .distinct()
                .count();
    }

    private User current(String username) { return users.findByUsername(username).orElseThrow(); }
    private ImportResponse toResponse(XlsxImportRun r) { return new ImportResponse(r.getId(), r.getStatus().name(), r.getTotalCells(), r.getMapped(), r.getUnknown(), r.getPendingQuestions()); }
    public record ImportResponse(Long id, String status, int totalCells, int mapped, int unknown, int pendingQuestions) {}
    public record ResolveRequest(String activityText, Long deloId, CreateDelo createDelo) {}
    public record CreateDelo(String title, Long projectId, Boolean supporting) {}
}
