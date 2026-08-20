package ru.wolf.api.gantt;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.delo.DeloProject;
import ru.wolf.api.delo.DeloProjectRepository;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.timeentry.DayBounds;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Гантт: дерево Проектов × ISO-недели, План на неделю + факт-часы из Записей времени.
 *
 * <p>Факт учитывает {@code hourAccountingMode} (PRIMARY_ONLY / ALL_PROJECTS).
 * Ad-hoc записи (без Дела) в факт проектов не входят.
 */
@RestController
@RequestMapping("/api/v1/gantt")
@RequiredArgsConstructor
public class GanttController {

    private static final int DEFAULT_WEEK_COUNT = 16;
    private static final int MAX_WEEK_COUNT = 52;

    private final ProjectRepository projectRepository;
    private final WeekPlanRepository weekPlanRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final DeloProjectRepository deloProjectRepository;
    private final UserRepository userRepository;
    private final GanttForecastService ganttForecastService;

    /**
     * Aggregate Gantt payload.
     *
     * <p>Default range: Monday of previous week → {@code weeks} columns (default 16).
     * Filters: {@code lifeAreaIds} (comma-separated), {@code onlyWithDates}.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<GanttResponse> getGantt(
            Authentication authentication,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "weeks", required = false) Integer weeks,
            @RequestParam(value = "lifeAreaIds", required = false) String lifeAreaIds,
            @RequestParam(value = "onlyWithDates", required = false, defaultValue = "false") boolean onlyWithDates
    ) {
        User user = currentUser(authentication);
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalTime dayEnd = user.getDayEnd() != null ? user.getDayEnd() : LocalTime.of(2, 0);

        int weekCount = weeks == null ? DEFAULT_WEEK_COUNT : weeks;
        if (weekCount < 1 || weekCount > MAX_WEEK_COUNT) {
            throw new IllegalArgumentException("weeks должен быть от 1 до " + MAX_WEEK_COUNT);
        }

        LocalDate rangeStartMonday = resolveRangeStart(from, zone);
        List<WeekColumn> weekColumns = buildWeekColumns(rangeStartMonday, weekCount, zone);
        WeekColumn first = weekColumns.get(0);
        WeekColumn last = weekColumns.get(weekColumns.size() - 1);

        Set<Long> areaFilter = parseLifeAreaIds(lifeAreaIds);

        List<Project> allProjects = projectRepository.findByUserOrderByTitleAsc(user);
        List<Project> filtered = allProjects.stream()
                .filter(p -> areaFilter.isEmpty() || areaFilter.contains(p.getLifeArea().getId()))
                .filter(p -> !onlyWithDates || p.getStartDate() != null || p.getEndDate() != null)
                .toList();

        // Keep parents of filtered children so tree nesting stays intact when filtering by dates
        if (onlyWithDates || !areaFilter.isEmpty()) {
            filtered = expandWithAncestors(allProjects, filtered);
        }

        Map<String, BigDecimal> planByKey = loadPlans(user, first, last);
        Map<String, BigDecimal> factByKey = computeFacts(user, dayEnd, weekColumns);

        List<ProjectRow> rows = buildProjectRows(filtered, weekColumns, planByKey, factByKey);

        return ResponseEntity.ok(new GanttResponse(
                user.getHourAccountingMode(),
                user.getTimezone(),
                first.getWeekStart(),
                last.getWeekEndExclusive(),
                weekColumns,
                rows
        ));
    }

    @GetMapping("/forecast")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ForecastResponse>> getForecast(Authentication authentication) {
        return ResponseEntity.ok(ganttForecastService.forecast(currentUser(authentication)));
    }

    /**
     * Upsert План на неделю for project×ISO-week.
     * {@code planHours == null} or zero → delete plan cell (clear).
     */
    @PutMapping("/week-plans")
    @Transactional
    public ResponseEntity<WeekPlanResponse> upsertWeekPlan(
            Authentication authentication,
            @Valid @RequestBody UpsertWeekPlanRequest request
    ) {
        User user = currentUser(authentication);
        validateIsoWeek(request.getIsoYear(), request.getIsoWeek());

        Project project = projectRepository.findByUserAndId(user, request.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Проект не найден"));

        BigDecimal hours = request.getPlanHours();
        boolean clear = hours == null || hours.compareTo(BigDecimal.ZERO) == 0;

        if (clear) {
            weekPlanRepository.findByUserAndProjectIdAndIsoYearAndIsoWeek(
                    user, project.getId(), request.getIsoYear(), request.getIsoWeek()
            ).ifPresent(weekPlanRepository::delete);
            return ResponseEntity.ok(new WeekPlanResponse(
                    project.getId(),
                    request.getIsoYear(),
                    request.getIsoWeek(),
                    null
            ));
        }

        if (hours.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("planHours не может быть отрицательным");
        }

        WeekPlan plan = weekPlanRepository
                .findByUserAndProjectIdAndIsoYearAndIsoWeek(
                        user, project.getId(), request.getIsoYear(), request.getIsoWeek())
                .orElseGet(() -> WeekPlan.builder()
                        .user(user)
                        .project(project)
                        .isoYear(request.getIsoYear())
                        .isoWeek(request.getIsoWeek())
                        .build());

        plan.setPlanHours(hours.setScale(2, RoundingMode.HALF_UP));
        WeekPlan saved = weekPlanRepository.save(plan);

        return ResponseEntity.ok(new WeekPlanResponse(
                project.getId(),
                saved.getIsoYear(),
                saved.getIsoWeek(),
                saved.getPlanHours()
        ));
    }

    // --- fact aggregation ---

    private Map<String, BigDecimal> computeFacts(User user, LocalTime dayEnd, List<WeekColumn> weeks) {
        LocalDate firstMonday = LocalDate.parse(weeks.get(0).getWeekStart());
        LocalDate lastNextMonday = LocalDate.parse(weeks.get(weeks.size() - 1).getWeekEndExclusive());
        LocalDateTime rangeFrom = DayBounds.forDay(firstMonday, dayEnd).start();
        LocalDateTime rangeTo = DayBounds.forDay(lastNextMonday, dayEnd).start();

        List<TimeEntry> entries = timeEntryRepository.findOverlapping(user.getId(), rangeFrom, rangeTo);

        // Preload delo→projects for entries that have delo
        Set<Long> deloIds = entries.stream()
                .filter(e -> e.getDelo() != null)
                .map(e -> e.getDelo().getId())
                .collect(Collectors.toSet());

        Map<Long, List<DeloProject>> linksByDelo = new HashMap<>();
        for (Long deloId : deloIds) {
            linksByDelo.put(deloId, deloProjectRepository.findByDeloId(deloId));
        }

        boolean primaryOnly = !"ALL_PROJECTS".equalsIgnoreCase(user.getHourAccountingMode());

        // week key -> projectId -> hours
        Map<String, Map<Long, BigDecimal>> acc = new HashMap<>();

        for (TimeEntry entry : entries) {
            // Fact = confirmed work only
            if (entry.getStatus() != TimeEntry.Status.DONE) {
                continue;
            }
            // Ad-hoc excluded from project fact
            if (entry.getDelo() == null) {
                continue;
            }

            List<DeloProject> links = linksByDelo.getOrDefault(entry.getDelo().getId(), List.of());
            if (links.isEmpty()) {
                continue;
            }

            List<Long> targetProjectIds;
            if (primaryOnly) {
                Long primaryId = links.stream()
                        .filter(l -> Boolean.TRUE.equals(l.getIsPrimary()))
                        .map(l -> l.getProject().getId())
                        .findFirst()
                        .orElse(null);
                // If no primary marked, skip (ambiguous) — primary is required when projects exist
                if (primaryId == null) {
                    continue;
                }
                targetProjectIds = List.of(primaryId);
            } else {
                targetProjectIds = links.stream()
                        .map(l -> l.getProject().getId())
                        .distinct()
                        .toList();
            }

            for (WeekColumn week : weeks) {
                LocalDate monday = LocalDate.parse(week.getWeekStart());
                LocalDate nextMonday = LocalDate.parse(week.getWeekEndExclusive());
                LocalDateTime wFrom = DayBounds.forDay(monday, dayEnd).start();
                LocalDateTime wTo = DayBounds.forDay(nextMonday, dayEnd).start();

                double overlapHours = overlapHours(entry.getStartAt(), entry.getEndAt(), wFrom, wTo);
                if (overlapHours <= 0) {
                    continue;
                }
                BigDecimal hours = BigDecimal.valueOf(overlapHours).setScale(2, RoundingMode.HALF_UP);
                String weekKey = weekKey(week.getIsoYear(), week.getIsoWeek());
                Map<Long, BigDecimal> byProject = acc.computeIfAbsent(weekKey, k -> new HashMap<>());
                for (Long pid : targetProjectIds) {
                    byProject.merge(pid, hours, BigDecimal::add);
                }
            }
        }

        Map<String, BigDecimal> flat = new HashMap<>();
        for (var weekEntry : acc.entrySet()) {
            for (var pe : weekEntry.getValue().entrySet()) {
                flat.put(cellKey(pe.getKey(), weekEntry.getKey()), pe.getValue());
            }
        }
        return flat;
    }

    /** Hours of overlap between [aStart,aEnd) and [bStart,bEnd). */
    static double overlapHours(LocalDateTime aStart, LocalDateTime aEnd,
                               LocalDateTime bStart, LocalDateTime bEnd) {
        LocalDateTime start = aStart.isAfter(bStart) ? aStart : bStart;
        LocalDateTime end = aEnd.isBefore(bEnd) ? aEnd : bEnd;
        if (!end.isAfter(start)) {
            return 0;
        }
        return Duration.between(start, end).toMinutes() / 60.0;
    }

    // --- helpers ---

    private LocalDate resolveRangeStart(String from, ZoneId zone) {
        if (from != null && !from.isBlank()) {
            LocalDate d = LocalDate.parse(from);
            return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        // Default: Monday of previous week
        LocalDate today = LocalDate.now(zone);
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return thisMonday.minusWeeks(1);
    }

    private List<WeekColumn> buildWeekColumns(LocalDate startMonday, int count, ZoneId zone) {
        LocalDate currentMonday = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<WeekColumn> cols = new ArrayList<>(count);
        LocalDate cursor = startMonday;
        for (int i = 0; i < count; i++) {
            int isoYear = cursor.get(WeekFields.ISO.weekBasedYear());
            int isoWeek = cursor.get(WeekFields.ISO.weekOfWeekBasedYear());
            LocalDate next = cursor.plusWeeks(1);
            String monthLabel = cursor.getMonth().getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("ru"));
            // Capitalize first letter for RU short month
            if (!monthLabel.isEmpty()) {
                monthLabel = Character.toUpperCase(monthLabel.charAt(0)) + monthLabel.substring(1);
            }
            boolean isCurrent = cursor.equals(currentMonday);
            cols.add(new WeekColumn(
                    isoYear,
                    isoWeek,
                    cursor.toString(),
                    next.toString(),
                    monthLabel,
                    cursor.getMonthValue(),
                    cursor.getYear(),
                    isCurrent
            ));
            cursor = next;
        }
        return cols;
    }

    private Map<String, BigDecimal> loadPlans(User user, WeekColumn first, WeekColumn last) {
        List<WeekPlan> plans = weekPlanRepository.findInWeekRange(
                user,
                first.getIsoYear(), first.getIsoWeek(),
                last.getIsoYear(), last.getIsoWeek()
        );
        // ISO week range query is approximate for year boundaries; filter precisely
        Map<String, BigDecimal> map = new HashMap<>();
        for (WeekPlan wp : plans) {
            if (isWeekInInclusiveRange(wp.getIsoYear(), wp.getIsoWeek(),
                    first.getIsoYear(), first.getIsoWeek(),
                    last.getIsoYear(), last.getIsoWeek())) {
                map.put(cellKey(wp.getProject().getId(), weekKey(wp.getIsoYear(), wp.getIsoWeek())),
                        wp.getPlanHours());
            }
        }
        return map;
    }

    private static boolean isWeekInInclusiveRange(int y, int w, int fy, int fw, int ty, int tw) {
        long key = y * 100L + w;
        long from = fy * 100L + fw;
        long to = ty * 100L + tw;
        return key >= from && key <= to;
    }

    private List<Project> expandWithAncestors(List<Project> all, List<Project> filtered) {
        Map<Long, Project> byId = all.stream().collect(Collectors.toMap(Project::getId, p -> p, (a, b) -> a));
        Set<Long> keep = filtered.stream().map(Project::getId).collect(Collectors.toCollection(HashSet::new));
        for (Project p : filtered) {
            Long parentId = p.getParent() != null ? p.getParent().getId() : null;
            while (parentId != null && keep.add(parentId)) {
                Project parent = byId.get(parentId);
                parentId = parent != null && parent.getParent() != null ? parent.getParent().getId() : null;
            }
        }
        return all.stream().filter(p -> keep.contains(p.getId())).toList();
    }

    private List<ProjectRow> buildProjectRows(
            List<Project> projects,
            List<WeekColumn> weeks,
            Map<String, BigDecimal> planByKey,
            Map<String, BigDecimal> factByKey
    ) {
        // Tree order: roots first, children nested by title
        Map<Long, List<Project>> byParent = new HashMap<>();
        for (Project p : projects) {
            Long key = p.getParent() != null ? p.getParent().getId() : null;
            byParent.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }
        for (List<Project> kids : byParent.values()) {
            kids.sort(Comparator.comparing(Project::getTitle, String.CASE_INSENSITIVE_ORDER));
        }

        List<ProjectRow> rows = new ArrayList<>();
        walkTree(null, 0, byParent, weeks, planByKey, factByKey, rows);

        // Orphans (parent not in filtered set) — already expanded with ancestors, but safety
        Set<Long> shown = rows.stream().map(ProjectRow::getId).collect(Collectors.toSet());
        for (Project p : projects) {
            if (!shown.contains(p.getId())) {
                rows.add(toRow(p, 0, weeks, planByKey, factByKey));
            }
        }
        return rows;
    }

    private void walkTree(
            Long parentId,
            int depth,
            Map<Long, List<Project>> byParent,
            List<WeekColumn> weeks,
            Map<String, BigDecimal> planByKey,
            Map<String, BigDecimal> factByKey,
            List<ProjectRow> acc
    ) {
        List<Project> kids = byParent.getOrDefault(parentId, List.of());
        for (Project p : kids) {
            acc.add(toRow(p, depth, weeks, planByKey, factByKey));
            walkTree(p.getId(), depth + 1, byParent, weeks, planByKey, factByKey, acc);
        }
    }

    private ProjectRow toRow(
            Project p,
            int depth,
            List<WeekColumn> weeks,
            Map<String, BigDecimal> planByKey,
            Map<String, BigDecimal> factByKey
    ) {
        List<CellHours> cells = new ArrayList<>(weeks.size());
        for (WeekColumn w : weeks) {
            String ck = cellKey(p.getId(), weekKey(w.getIsoYear(), w.getIsoWeek()));
            cells.add(new CellHours(
                    w.getIsoYear(),
                    w.getIsoWeek(),
                    planByKey.get(ck),
                    factByKey.getOrDefault(ck, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
            ));
        }
        return new ProjectRow(
                p.getId(),
                p.getParent() != null ? p.getParent().getId() : null,
                p.getLifeArea().getId(),
                p.getLifeArea().getName(),
                p.getTitle(),
                p.getStartDate() != null ? p.getStartDate().toString() : null,
                p.getEndDate() != null ? p.getEndDate().toString() : null,
                p.getTotalPlanHours(),
                depth,
                cells
        );
    }

    private Set<Long> parseLifeAreaIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        Set<Long> ids = new HashSet<>();
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) continue;
            try {
                ids.add(Long.parseLong(t));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Некорректный lifeAreaIds: " + raw);
            }
        }
        return ids;
    }

    private void validateIsoWeek(int year, int week) {
        if (week < 1 || week > 53) {
            throw new IllegalArgumentException("isoWeek должен быть от 1 до 53");
        }
        try {
            LocalDate monday = LocalDate.of(year, 1, 4)
                    .with(WeekFields.ISO.weekOfWeekBasedYear(), week)
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            int resolvedWeek = monday.get(WeekFields.ISO.weekOfWeekBasedYear());
            int resolvedYear = monday.get(WeekFields.ISO.weekBasedYear());
            if (resolvedWeek != week || resolvedYear != year) {
                throw new IllegalArgumentException("Некорректная ISO-неделя: " + year + "-W" + week);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Некорректная ISO-неделя: " + year + "-W" + week);
        }
    }

    private static String weekKey(int year, int week) {
        return year + "-W" + week;
    }

    private static String cellKey(Long projectId, String weekKey) {
        return projectId + "|" + weekKey;
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    // --- DTOs ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GanttResponse {
        private String hourAccountingMode;
        private String timezone;
        private String rangeStart;
        private String rangeEndExclusive;
        private List<WeekColumn> weeks;
        private List<ProjectRow> projects;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeekColumn {
        private int isoYear;
        private int isoWeek;
        private String weekStart;
        private String weekEndExclusive;
        private String monthLabel;
        private int month;
        private int calendarYear;
        private boolean current;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectRow {
        private Long id;
        private Long parentId;
        private Long lifeAreaId;
        private String lifeAreaName;
        private String title;
        private String startDate;
        private String endDate;
        private BigDecimal totalPlanHours;
        private int depth;
        private List<CellHours> cells;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CellHours {
        private int isoYear;
        private int isoWeek;
        /** null = no plan set */
        private BigDecimal planHours;
        private BigDecimal factHours;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpsertWeekPlanRequest {
        @NotNull
        private Long projectId;
        @NotNull
        private Integer isoYear;
        @NotNull
        @Min(1)
        @Max(53)
        private Integer isoWeek;
        /** null or 0 clears the plan */
        @DecimalMin(value = "0.0", inclusive = true)
        private BigDecimal planHours;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeekPlanResponse {
        private Long projectId;
        private Integer isoYear;
        private Integer isoWeek;
        private BigDecimal planHours;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForecastResponse {
        private Long projectId;
        private LocalDate planEnd;
        private LocalDate forecastEnd;
        private BigDecimal weeklyAvg;
        private BigDecimal remaining;
    }
}
