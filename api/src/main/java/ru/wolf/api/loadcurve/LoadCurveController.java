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
package ru.wolf.api.loadcurve;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.routine.Routine;
import ru.wolf.api.routine.RoutineRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class LoadCurveController {
    private final LoadCurveEntryRepository entries;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final RoutineRepository routines;

    @GetMapping("/api/v1/projects/{id}/load-curve")
    @Transactional(readOnly = true)
    public List<Response> project(Authentication auth, @PathVariable Long id) { User user = current(auth); Project p = projects.findByUserAndId(user, id).orElseThrow(); return entries.findByProjectIdOrderByWeekStart(p.getId()).stream().map(this::response).toList(); }
    @GetMapping("/api/v1/routines/{id}/load-curve")
    @Transactional(readOnly = true)
    public List<Response> routine(Authentication auth, @PathVariable Long id) { User user = current(auth); Routine r = routines.findByUserAndId(user, id).orElseThrow(); return entries.findByRoutineIdOrderByWeekStart(r.getId()).stream().map(this::response).toList(); }
    @PutMapping("/api/v1/projects/{id}/load-curve")
    @Transactional
    public Response putProject(Authentication auth, @PathVariable Long id, @Valid @RequestBody Request request) { User user = current(auth); Project p = projects.findByUserAndId(user, id).orElseThrow(); return save(request, p, null, user); }
    @PutMapping("/api/v1/routines/{id}/load-curve")
    @Transactional
    public Response putRoutine(Authentication auth, @PathVariable Long id, @Valid @RequestBody Request request) { User user = current(auth); Routine r = routines.findByUserAndId(user, id).orElseThrow(); return save(request, null, r, user); }
    @DeleteMapping("/api/v1/projects/{id}/load-curve/{entryId}")
    @Transactional public ResponseEntity<Void> deleteProject(Authentication a,@PathVariable Long id,@PathVariable Long entryId){ User u=current(a); projects.findByUserAndId(u,id).orElseThrow(); entries.delete(entries.findById(entryId).filter(e->e.getProject()!=null&&e.getProject().getId().equals(id)).orElseThrow()); return ResponseEntity.noContent().build(); }
    @DeleteMapping("/api/v1/routines/{id}/load-curve/{entryId}")
    @Transactional public ResponseEntity<Void> deleteRoutine(Authentication a,@PathVariable Long id,@PathVariable Long entryId){ User u=current(a); routines.findByUserAndId(u,id).orElseThrow(); entries.delete(entries.findById(entryId).filter(e->e.getRoutine()!=null&&e.getRoutine().getId().equals(id)).orElseThrow()); return ResponseEntity.noContent().build(); }
    private Response save(Request req, Project p, Routine r, User user) {
        LocalDate week = req.weekStart().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = req.endWeek() == null ? week : req.endWeek().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (end.isBefore(week)) throw new IllegalArgumentException("Конец сегмента не может быть раньше начала");
        List<LoadCurveEntry> owned = p != null ? entries.findByProjectIdOrderByWeekStart(p.getId()) : entries.findByRoutineIdOrderByWeekStart(r.getId());
        if (!end.equals(week) && owned.stream().anyMatch(x -> !x.getWeekStart().isBefore(week) && !x.getWeekStart().isAfter(end)))
            throw new IllegalArgumentException("Интервалы кривой не должны пересекаться");
        LoadCurveEntry first = null;
        for (LocalDate cursor = week; !cursor.isAfter(end); cursor = cursor.plusWeeks(1)) {
            LocalDate current = cursor;
            LoadCurveEntry entry = owned.stream().filter(x -> x.getWeekStart().equals(current)).findFirst().orElseGet(() -> LoadCurveEntry.builder().project(p).routine(r).weekStart(current).build());
            entry.setHours(req.hours().setScale(2));
            if (first == null) first = entries.save(entry); else entries.save(entry);
        }
        return response(first);
    }
    private User current(Authentication a){return users.findByUsername(a.getName()).orElseThrow();} private Response response(LoadCurveEntry e){return new Response(e.getId(),e.getWeekStart(),e.getHours(),e.getProject()!=null?"project":"routine");}
    public record Request(@NotNull LocalDate weekStart,@NotNull @DecimalMin("0.0") BigDecimal hours, LocalDate endWeek){} public record Response(Long id,LocalDate weekStart,BigDecimal hours,String ownerType){}
}
