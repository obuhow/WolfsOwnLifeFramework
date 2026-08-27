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
package ru.wolf.api.delo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.backlog.BacklogItem;
import ru.wolf.api.backlog.BacklogItemRepository;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.lifearea.LifeArea;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeloImportService {

    private final DeloRepository deloRepository;
    private final DeloProjectRepository deloProjectRepository;
    private final ProjectRepository projectRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final LifeAreaRepository lifeAreaRepository;
    private final BacklogItemRepository backlogItemRepository;
    private final UserRepository userRepository;

    @Transactional
    public ImportResponse importCsv(String username, byte[] fileBytes, boolean addToCurrentWeek, boolean skipOverlapCheck) {
        if (fileBytes.length == 0) {
            throw new IllegalArgumentException("CSV-файл пуст");
        }

        User user = currentUser(username);
        List<ImportedRow> rows = parse(fileBytes);

        // Deduplicate Delos by title (case-insensitive)
        Map<String, Delo> deloByTitle = new HashMap<>();
        // 1. Collect unique titles from CSV
        Set<String> uniqueTitles = rows.stream()
                .map(ImportedRow::title)
                .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));
        // 2. Load existing Delos for this user (case-insensitive)
        List<String> lowerTitles = uniqueTitles.stream()
                .map(t -> t.toLowerCase(Locale.ROOT))
                .toList();
        List<Delo> existingDelos = deloRepository.findByUserAndTitleInIgnoreCase(user, lowerTitles);
        for (Delo d : existingDelos) {
            deloByTitle.put(d.getTitle().toLowerCase(Locale.ROOT), d);
        }
        // 3. Create missing Delos
        for (String title : uniqueTitles) {
            String key = title.toLowerCase(Locale.ROOT);
            if (!deloByTitle.containsKey(key)) {
                Delo newDelo = deloRepository.save(Delo.builder()
                        .user(user)
                        .title(title)
                        .build());
                deloByTitle.put(key, newDelo);
            }
        }

        List<Delo> saved = new ArrayList<>();

        // If skipOverlapCheck, pre-clear all overlapping intervals from CSV
        if (skipOverlapCheck) {
            for (ImportedRow row : rows) {
                LocalDateTime startAt = row.date().atTime(row.startAt());
                LocalDateTime endAt = row.date().atTime(row.endAt());
                clearOverlapping(user.getId(), startAt, endAt);
            }
        }

        for (int i = 0; i < rows.size(); i++) {
            ImportedRow row = rows.get(i);
            Delo delo = deloByTitle.get(row.title().toLowerCase(Locale.ROOT));
            saved.add(delo);

            List<Project> projects = resolveProjects(user, row.projectTitles(), row.lifeArea(), row.lineNumber());
            for (int projectIndex = 0; projectIndex < projects.size(); projectIndex++) {
                Project project = projects.get(projectIndex);
                deloProjectRepository.save(DeloProject.builder()
                        .id(new DeloProjectId(delo.getId(), project.getId()))
                        .delo(delo)
                        .project(project)
                        .isPrimary(projectIndex == 0)
                        .build());
            }

            LocalDateTime startAt = row.date().atTime(row.startAt());
            LocalDateTime endAt = row.date().atTime(row.endAt());
            if (!endAt.isAfter(startAt)) {
                throw new IllegalArgumentException("Строка " + row.lineNumber() + ": endAt должен быть позже startAt");
            }
            long durationMinutes = java.time.Duration.between(startAt, endAt).toMinutes();
            if (startAt.getMinute() % 15 != 0 || startAt.getSecond() != 0 || startAt.getNano() != 0
                    || endAt.getMinute() % 15 != 0 || endAt.getSecond() != 0 || endAt.getNano() != 0
                    || durationMinutes % 15 != 0) {
                throw new IllegalArgumentException("Строка " + row.lineNumber() + ": startAt/endAt должны быть выровнены по 15 минутам");
            }
            List<TimeEntry> overlaps = timeEntryRepository.findOverlapping(user.getId(), startAt, endAt);
            if (!skipOverlapCheck && !overlaps.isEmpty()) {
                TimeEntry overlap = overlaps.get(0);
                String existing = overlap.getDelo() != null ? overlap.getDelo().getTitle() : overlap.getAdHocText();
                throw new IllegalArgumentException("Строка " + row.lineNumber()
                        + ": интервал " + startAt + "–" + endAt
                        + " пересекается с существующей Записью времени"
                        + (existing == null || existing.isBlank() ? "" : " «" + existing + "»"));
            }
            timeEntryRepository.save(TimeEntry.builder()
                    .user(user)
                    .delo(delo)
                    .startAt(startAt)
                    .endAt(endAt)
                    .status(TimeEntry.Status.PLANNED)
                    .build());
        }

        if (addToCurrentWeek) {
            LocalDate today = LocalDate.now(ZoneId.of(user.getTimezone()));
            WeekFields fields = WeekFields.ISO;
            int year = today.get(fields.weekBasedYear());
            int week = today.get(fields.weekOfWeekBasedYear());
            String period = "%d-W%02d".formatted(year, week);
            int position = 0;
            for (Delo delo : new LinkedHashSet<>(saved)) {
                if (backlogItemRepository.findByUserAndDeloIdAndScopeAndPeriodId(user, delo.getId(), BacklogItem.Scope.WEEK, period).isEmpty()) {
                    backlogItemRepository.save(BacklogItem.builder().user(user).delo(delo).scope(BacklogItem.Scope.WEEK).periodId(period).position(position++).build());
                }
            }
        }

        // Count unique Delos created (not rows)
        int uniqueCreated = (int) deloByTitle.values().stream()
                .filter(d -> d.getId() != null)
                .count();

        return new ImportResponse(saved.size(), addToCurrentWeek);
    }

    private List<ImportedRow> parse(byte[] fileBytes) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new java.io.ByteArrayInputStream(fileBytes), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("CSV-файл пуст");
            }
            List<String> headers = parseLine(headerLine.replaceFirst("^\\uFEFF", ""));
            int titleIndex = columnIndex(headers, "title", true);
            int dateIndex = columnIndex(headers, "date", true);
            int startAtIndex = columnIndex(headers, "startAt", true);
            int endAtIndex = columnIndex(headers, "endAt", true);
            int descriptionIndex = columnIndex(headers, "description", false);
            int modeIndex = columnIndex(headers, "executionMode", true);
            int projectsIndex = columnIndex(headers, "projects", false);
            int lifeAreaIndex = columnIndex(headers, "lifeArea", false);
            if (lifeAreaIndex < 0) {
                lifeAreaIndex = columnIndex(headers, "Область жизни", false);
            }

            List<ImportedRow> rows = new ArrayList<>();
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = normalizeValues(parseLine(line), headers.size(), descriptionIndex);
                String title = value(values, titleIndex).trim();
                if (title.isBlank()) {
                    throw new IllegalArgumentException("Строка " + lineNumber + ": title обязателен");
                }
                if (title.length() > 200) {
                    throw new IllegalArgumentException("Строка " + lineNumber + ": title длиннее 200 символов");
                }
                String description = value(values, descriptionIndex).trim();
                Delo.ExecutionMode mode = parseMode(value(values, modeIndex), lineNumber);
                LocalDate date = parseDate(value(values, dateIndex), lineNumber);
                LocalTime startAt = parseTime(value(values, startAtIndex), "startAt", lineNumber);
                LocalTime endAt = parseTime(value(values, endAtIndex), "endAt", lineNumber);
                String lifeArea = value(values, lifeAreaIndex).trim();
                rows.add(new ImportedRow(title, date, startAt, endAt,
                        description.isBlank() ? null : description, mode,
                        splitProjects(value(values, projectsIndex)), lifeArea, lineNumber));
            }
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("CSV-файл не содержит Дел");
            }
            return rows;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Не удалось прочитать CSV-файл", ex);
        }
    }

    private int columnIndex(List<String> headers, String name, boolean required) {
        int index = headers.stream().map(String::trim).map(String::toLowerCase)
                .toList().indexOf(name.toLowerCase(Locale.ROOT));
        if (index < 0 && required) {
            throw new IllegalArgumentException("В CSV отсутствует обязательная колонка: " + name);
        }
        return index;
    }

    private String value(List<String> values, int index) {
        return index >= 0 && index < values.size() ? values.get(index) : "";
    }

    /**
     * Be tolerant of legacy comma-separated files where description contains an
     * unquoted comma. The canonical format still requires CSV quoting for commas.
     */
    private List<String> normalizeValues(List<String> values, int expectedSize, int descriptionIndex) {
        if (descriptionIndex < 0 || values.size() <= expectedSize) {
            return values;
        }
        int overflow = values.size() - expectedSize;
        List<String> normalized = new ArrayList<>();
        normalized.addAll(values.subList(0, descriptionIndex));
        normalized.add(String.join(",", values.subList(descriptionIndex, descriptionIndex + overflow + 1)));
        normalized.addAll(values.subList(descriptionIndex + overflow + 1, values.size()));
        return normalized;
    }

    private Delo.ExecutionMode parseMode(String raw, int lineNumber) {
        if (raw == null || raw.isBlank()) {
            return Delo.ExecutionMode.SELF;
        }
        try {
            return Delo.ExecutionMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Строка " + lineNumber + ": неизвестный executionMode " + raw);
        }
    }

    private LocalDate parseDate(String raw, int lineNumber) {
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Строка " + lineNumber + ": date должен быть в формате YYYY-MM-DD");
        }
    }

    private LocalTime parseTime(String raw, String column, int lineNumber) {
        try {
            return LocalTime.parse(raw.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Строка " + lineNumber + ": " + column + " должен быть в формате HH:mm");
        }
    }

    private List<String> splitProjects(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return java.util.Arrays.stream(raw.split("\\|"))
                .map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
    }

    private List<Project> resolveProjects(User user, List<String> titles, String lifeAreaName, int lineNumber) {
        List<Project> projects = new ArrayList<>();
        final LifeArea lifeArea;
        if (titles.isEmpty()) {
            lifeArea = null;
        } else {
            if (lifeAreaName.isBlank()) {
                throw new IllegalArgumentException("Строка " + lineNumber + ": lifeArea обязателен для создания Проекта");
            }
            lifeArea = lifeAreaRepository.findByUserOrderBySortOrderAscNameAsc(user).stream()
                    .filter(area -> area.getName().equalsIgnoreCase(lifeAreaName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Строка " + lineNumber + ": Область жизни не найдена: " + lifeAreaName));
        }
        for (String title : titles) {
            Project project = projectRepository.findByUserAndTitleIgnoreCase(user, title).orElseGet(() ->
                    projectRepository.save(Project.builder()
                            .user(user)
                            .lifeArea(lifeArea)
                            .title(title)
                            .build()));
            projects.add(project);
        }
        return projects;
    }

    private List<String> parseLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("CSV содержит незакрытую кавычку");
        }
        values.add(current.toString());
        return values;
    }



    private User currentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private void clearOverlapping(Long userId, LocalDateTime from, LocalDateTime to) {
        List<TimeEntry> overlaps = timeEntryRepository.findOverlapping(userId, from, to);
        for (TimeEntry e : overlaps) {
            boolean startsInside = !e.getStartAt().isBefore(from) && e.getStartAt().isBefore(to);
            boolean endsInside = e.getEndAt().isAfter(from) && !e.getEndAt().isAfter(to);
            boolean coversAll = e.getStartAt().isBefore(from) && e.getEndAt().isAfter(to);

            if (coversAll) {
                LocalDateTime rightEnd = e.getEndAt();
                e.setEndAt(from);
                timeEntryRepository.save(e);
                if (to.isBefore(rightEnd)) {
                    timeEntryRepository.save(TimeEntry.builder()
                            .user(e.getUser())
                            .delo(e.getDelo())
                            .adHocText(e.getAdHocText())
                            .startAt(to)
                            .endAt(rightEnd)
                            .status(e.getStatus())
                            .build());
                }
            } else if (startsInside && endsInside) {
                timeEntryRepository.delete(e);
            } else if (startsInside) {
                e.setStartAt(to);
                if (!e.getEndAt().isAfter(e.getStartAt())) {
                    timeEntryRepository.delete(e);
                } else {
                    timeEntryRepository.save(e);
                }
            } else if (endsInside || (e.getStartAt().isBefore(from) && e.getEndAt().isAfter(from))) {
                e.setEndAt(from);
                if (!e.getEndAt().isAfter(e.getStartAt())) {
                    timeEntryRepository.delete(e);
                } else {
                    timeEntryRepository.save(e);
                }
            } else {
                timeEntryRepository.delete(e);
            }
        }
    }

    private record ImportedRow(
            String title,
            LocalDate date,
            LocalTime startAt,
            LocalTime endAt,
            String description,
            Delo.ExecutionMode executionMode,
            List<String> projectTitles,
            String lifeArea,
            int lineNumber
    ) {}

    @Data
    @AllArgsConstructor
    public static class ImportResponse {
        private int imported;
        private boolean addedToCurrentWeek;
    }
}
