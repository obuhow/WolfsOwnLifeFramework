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
package ru.wolf.api.routine;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import ru.wolf.api.routine.dto.*;
import ru.wolf.api.goal.Goal;
import ru.wolf.api.goal.GoalRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoutineService {

    private final RoutineRepository routineRepository;
    private final RoutineScheduleRepository scheduleRepository;
    private final RoutineGoalRepository routineGoalRepository;
    private final GoalRepository goalRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ResponseEntity<List<RoutineResponse>> list(String username,
                                                       boolean includeArchived) {
        User user = currentUser(username);
        return ResponseEntity.ok(routineRepository.findByUserAndArchivedOrderByTitleAsc(user, includeArchived)
                .stream().map(this::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public ResponseEntity<RoutineResponse> get(String username, Long id) {
        return ResponseEntity.ok(toResponse(findRoutine(currentUser(username), id)));
    }

    @Transactional
    public ResponseEntity<RoutineResponse> create(String username,
                                                   RoutineRequest request) {
        User user = currentUser(username);
        Routine routine = Routine.builder()
                .user(user)
                .title(request.title().trim())
                .description(normalize(request.description()))
                .weeklyHours(request.weeklyHours().setScale(2))
                .color(normalize(request.color()))
                .icon(normalize(request.icon()))
                .archived(false)
                .build();
        return ResponseEntity.ok(toResponse(routineRepository.save(routine)));
    }

    @Transactional
    public ResponseEntity<RoutineResponse> update(String username,
                                                   Long id,
                                                   RoutineRequest request) {
        Routine routine = findRoutine(currentUser(username), id);
        routine.setTitle(request.title().trim());
        routine.setDescription(normalize(request.description()));
        routine.setWeeklyHours(request.weeklyHours().setScale(2));
        routine.setColor(normalize(request.color()));
        routine.setIcon(normalize(request.icon()));
        return ResponseEntity.ok(toResponse(routineRepository.save(routine)));
    }

    @Transactional
    public ResponseEntity<RoutineResponse> archive(String username, Long id) {
        Routine routine = findRoutine(currentUser(username), id);
        routine.setArchived(!routine.isArchived());
        return ResponseEntity.ok(toResponse(routineRepository.save(routine)));
    }

    @Transactional
    public ResponseEntity<ScheduleResponse> addSchedule(String username,
                                                         Long id,
                                                         ScheduleRequest request) {
        Routine routine = findRoutine(currentUser(username), id);
        LocalTime start = LocalTime.parse(request.startTime());
        LocalTime end = LocalTime.parse(request.endTime());
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Время окончания должно быть позже времени начала");
        }
        RoutineSchedule saved = scheduleRepository.save(RoutineSchedule.builder()
                .routine(routine)
                .dayOfWeek(DayOfWeek.valueOf(request.dayOfWeek().toUpperCase()))
                .startTime(start)
                .endTime(end)
                .build());
        return ResponseEntity.ok(toScheduleResponse(saved));
    }

    @Transactional
    public ResponseEntity<Void> deleteSchedule(String username,
                                                Long id,
                                                Long scheduleId) {
        Routine routine = findRoutine(currentUser(username), id);
        RoutineSchedule schedule = scheduleRepository.findById(scheduleId)
                .filter(item -> item.getRoutine().getId().equals(routine.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Расписание не найдено"));
        scheduleRepository.delete(schedule);
        return ResponseEntity.noContent().build();
    }

    @Transactional
    public ResponseEntity<GoalLinkResponse> linkGoal(String username,
                                                      Long id,
                                                      Long goalId) {
        User user = currentUser(username);
        Routine routine = findRoutine(user, id);
        Goal goal = goalRepository.findByUserAndId(user, goalId)
                .orElseThrow(() -> new IllegalArgumentException("Цель не найдена"));
        RoutineGoalId linkId = new RoutineGoalId(routine.getId(), goal.getId());
        if (!routineGoalRepository.existsById(linkId)) {
            routineGoalRepository.save(RoutineGoal.builder().id(linkId).routine(routine).goal(goal).build());
        }
        return ResponseEntity.ok(new GoalLinkResponse(goal.getId(), goal.getTitle()));
    }

    @Transactional
    public ResponseEntity<Void> unlinkGoal(String username,
                                            Long id,
                                            Long goalId) {
        User user = currentUser(username);
        Routine routine = findRoutine(user, id);
        goalRepository.findByUserAndId(user, goalId)
                .orElseThrow(() -> new IllegalArgumentException("Цель не найдена"));
        routineGoalRepository.deleteById(new RoutineGoalId(routine.getId(), goalId));
        return ResponseEntity.noContent().build();
    }

    private Routine findRoutine(User user, Long id) {
        return routineRepository.findByUserAndId(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Рутина не найдена"));
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private RoutineResponse toResponse(Routine routine) {
        List<ScheduleResponse> schedules = scheduleRepository.findByRoutineIdOrderByDayOfWeekAscStartTimeAsc(routine.getId())
                .stream().map(this::toScheduleResponse).toList();
        List<GoalLinkResponse> goals = routineGoalRepository.findByRoutineId(routine.getId()).stream()
                .map(link -> new GoalLinkResponse(link.getGoal().getId(), link.getGoal().getTitle())).toList();
        return new RoutineResponse(routine.getId(), routine.getTitle(), routine.getDescription(), routine.getWeeklyHours(),
                routine.getColor(), routine.getIcon(), routine.isArchived(), schedules,
                goals.stream().map(GoalLinkResponse::goalId).toList(), goals);
    }

    private ScheduleResponse toScheduleResponse(RoutineSchedule schedule) {
        return new ScheduleResponse(schedule.getId(), schedule.getDayOfWeek().name(),
                schedule.getStartTime().toString(), schedule.getEndTime().toString());
    }

    private String normalize(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }










}

// end
