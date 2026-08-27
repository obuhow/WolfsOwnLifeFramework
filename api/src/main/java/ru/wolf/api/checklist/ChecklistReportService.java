package ru.wolf.api.checklist;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import ru.wolf.api.checklist.dto.*;
import ru.wolf.api.focus.*;
import ru.wolf.api.user.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class ChecklistReportService {
    private final UserRepository users;
    private final DailyChecklistItemRepository checklist;
    private final FocusSessionRepository sessions;
    private final FocusDistractionRepository distractions;

    @Transactional(readOnly = true)
    public ReportResponse report(String username, LocalDate from, LocalDate to) {
        User user = current(username);
        LocalDate end = to == null ? LocalDate.now(ZoneId.of(user.getTimezone())) : to;
        LocalDate start = from == null ? end.minusDays(13) : from;
        if (end.isBefore(start) || start.plusDays(366).isBefore(end))
            throw new IllegalArgumentException("Период отчёта должен быть от 1 до 367 дней");
        List<FocusSession> focus = sessions.findByUserAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtAsc(
                user, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        List<DayResponse> days = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            LocalDate reportDate = date;
            List<ItemResponse> items = checklist.findForDate(user, reportDate).stream()
                    .map(i -> new ItemResponse(i.getTitle(), i.getDelo() == null ? null : i.getDelo().getId(),
                            i.getDelo() == null ? null : i.getDelo().getTitle(), i.isDone(), i.getDoneAt())).toList();
            List<DistractionResponse> switches = focus.stream()
                    .filter(s -> s.getStartedAt().toLocalDate().equals(reportDate))
                    .flatMap(s -> distractions.findBySessionIdOrderByAtAsc(s.getId()).stream()
                            .map(d -> new DistractionResponse(d.getAt(), d.getDelo() == null ? d.getText() : d.getDelo().getTitle(), d.getMinutes())))
                    .toList();
            days.add(new DayResponse(reportDate, items, switches,
                    (int) items.stream().filter(ItemResponse::done).count(), items.size()));
        }
        int checked = days.stream().mapToInt(DayResponse::checkedCount).sum();
        int total = days.stream().mapToInt(DayResponse::totalCount).sum();
        int switchCount = days.stream().mapToInt(d -> d.distractions().size()).sum();
        return new ReportResponse(start, end, days, checked, total, switchCount);
    }

    @Transactional(readOnly = true)
    public ExportResult export(String username, String format, LocalDate from, LocalDate to) {
        ReportResponse data = report(username, from, to);
        boolean csv = "csv".equalsIgnoreCase(format);
        String body = csv ? csv(data) : markdown(data);
        return new ExportResult(body.getBytes(StandardCharsets.UTF_8), csv ? "text/csv; charset=UTF-8" : "text/markdown",
                "checklist-report." + (csv ? "csv" : "md"));
    }

    public record ExportResult(byte[] content, String contentType, String filename) {}

    private String markdown(ReportResponse r) { StringBuilder b = new StringBuilder("# Отчёт — чек-лист\n\nПериод: ").append(r.from()).append(" — ").append(r.to()).append("\n\nОтмечено: ").append(r.checkedTotal()).append(" из ").append(r.itemsTotal()).append("\nПереключений: ").append(r.distractionsTotal()).append("\n"); for (DayResponse d : r.days()) { b.append("\n## ").append(d.date()).append("\n"); if (d.items().isEmpty()) b.append("Пусто\n"); for (ItemResponse i : d.items()) b.append(i.done() ? "- [x] " : "- [ ] ").append(i.title()).append(i.deloTitle() == null ? "" : " — "+i.deloTitle()).append("\n"); for (DistractionResponse x : d.distractions()) b.append("- переключение на ").append(x.target() == null ? "текст" : x.target()).append(x.minutes() == null ? "" : " ("+x.minutes()+" мин)").append("\n"); } return b.toString(); }
    private String csv(ReportResponse r) { StringBuilder b = new StringBuilder("date,type,title,deloTitle,done,doneAt,minutes\n"); for (DayResponse d : r.days()) { for (ItemResponse i : d.items()) b.append(d.date()).append(",checklist,\"").append(csv(i.title())).append("\",\"").append(csv(i.deloTitle())).append("\",").append(i.done()).append(",").append(i.doneAt() == null ? "" : i.doneAt()).append(",\n"); for (DistractionResponse x : d.distractions()) b.append(d.date()).append(",switch,\"").append(csv(x.target())).append("\",,,,\"").append(x.minutes() == null ? "" : x.minutes()).append("\n"); } return b.toString(); }
    private String csv(String v) { return v == null ? "" : v.replace("\"", "\"\""); }
    private User current(String username) { return users.findByUsername(username).orElseThrow(); }
}
