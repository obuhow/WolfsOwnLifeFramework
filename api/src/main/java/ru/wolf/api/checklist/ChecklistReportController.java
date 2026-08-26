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
package ru.wolf.api.checklist;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.focus.*;
import ru.wolf.api.user.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/reports/checklist")
@RequiredArgsConstructor
public class ChecklistReportController {
    private final UserRepository users;
    private final DailyChecklistItemRepository checklist;
    private final FocusSessionRepository sessions;
    private final FocusDistractionRepository distractions;

    @GetMapping
    @Transactional(readOnly = true)
    public ReportResponse report(Authentication auth,
                                 @RequestParam(required = false) LocalDate from,
                                 @RequestParam(required = false) LocalDate to) {
        User user = current(auth);
        LocalDate end = to == null ? LocalDate.now(ZoneId.of(user.getTimezone())) : to;
        LocalDate start = from == null ? end.minusDays(13) : from;
        if (end.isBefore(start) || start.plusDays(366).isBefore(end)) throw new IllegalArgumentException("Период отчёта должен быть от 1 до 367 дней");
        List<FocusSession> focus = sessions.findByUserAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtAsc(user, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        List<DayResponse> days = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            LocalDate reportDate = date;
            List<ItemResponse> items = checklist.findForDate(user, reportDate).stream().map(i -> new ItemResponse(i.getTitle(), i.getDelo() == null ? null : i.getDelo().getId(), i.getDelo() == null ? null : i.getDelo().getTitle(), i.isDone(), i.getDoneAt())).toList();
            List<DistractionResponse> switches = focus.stream().filter(s -> s.getStartedAt().toLocalDate().equals(reportDate)).flatMap(s -> distractions.findBySessionIdOrderByAtAsc(s.getId()).stream().map(d -> new DistractionResponse(d.getAt(), d.getDelo() == null ? d.getText() : d.getDelo().getTitle(), d.getMinutes()))).toList();
            days.add(new DayResponse(reportDate, items, switches, (int) items.stream().filter(ItemResponse::done).count(), items.size()));
        }
        int checked = days.stream().mapToInt(DayResponse::checkedCount).sum();
        int total = days.stream().mapToInt(DayResponse::totalCount).sum();
        int switchCount = days.stream().mapToInt(d -> d.distractions().size()).sum();
        return new ReportResponse(start, end, days, checked, total, switchCount);
    }

    @GetMapping("/export")
    @Transactional(readOnly = true)
    public ResponseEntity<ByteArrayResource> export(Authentication auth, @RequestParam(defaultValue = "md") String format,
                                                     @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        ReportResponse data = report(auth, from, to);
        String body = "csv".equalsIgnoreCase(format) ? csv(data) : markdown(data);
        MediaType type = "csv".equalsIgnoreCase(format) ? MediaType.parseMediaType("text/csv; charset=UTF-8") : MediaType.TEXT_MARKDOWN;
        return ResponseEntity.ok().contentType(type).header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=checklist-report." + ("csv".equalsIgnoreCase(format) ? "csv" : "md")).body(new ByteArrayResource(body.getBytes(StandardCharsets.UTF_8)));
    }

    private String markdown(ReportResponse r) { StringBuilder b = new StringBuilder("# Отчёт — чек-лист\n\nПериод: ").append(r.from()).append(" — ").append(r.to()).append("\n\nОтмечено: ").append(r.checkedTotal()).append(" из ").append(r.itemsTotal()).append("\nПереключений: ").append(r.distractionsTotal()).append("\n"); for (DayResponse d : r.days()) { b.append("\n## ").append(d.date()).append("\n"); if (d.items().isEmpty()) b.append("Пусто\n"); for (ItemResponse i : d.items()) b.append(i.done() ? "- [x] " : "- [ ] ").append(i.title()).append(i.deloTitle() == null ? "" : " — "+i.deloTitle()).append("\n"); for (DistractionResponse x : d.distractions()) b.append("- переключение на ").append(x.target() == null ? "текст" : x.target()).append(x.minutes() == null ? "" : " ("+x.minutes()+" мин)").append("\n"); } return b.toString(); }
    private String csv(ReportResponse r) { StringBuilder b = new StringBuilder("date,type,title,deloTitle,done,doneAt,minutes\n"); for (DayResponse d : r.days()) { for (ItemResponse i : d.items()) b.append(d.date()).append(",checklist,\"").append(csv(i.title())).append("\",\"").append(csv(i.deloTitle())).append("\",").append(i.done()).append(",").append(i.doneAt() == null ? "" : i.doneAt()).append(",\n"); for (DistractionResponse x : d.distractions()) b.append(d.date()).append(",switch,\"").append(csv(x.target())).append("\",,,,\"").append(x.minutes() == null ? "" : x.minutes()).append("\n"); } return b.toString(); }
    private String csv(String v) { return v == null ? "" : v.replace("\"", "\"\""); }
    private User current(Authentication a) { return users.findByUsername(a.getName()).orElseThrow(); }
    public record ReportResponse(LocalDate from, LocalDate to, List<DayResponse> days, int checkedTotal, int itemsTotal, int distractionsTotal) {}
    public record DayResponse(LocalDate date, List<ItemResponse> items, List<DistractionResponse> distractions, int checkedCount, int totalCount) {}
    public record ItemResponse(String title, Long deloId, String deloTitle, boolean done, Instant doneAt) {}
    public record DistractionResponse(LocalDateTime at, String target, Integer minutes) {}
}
