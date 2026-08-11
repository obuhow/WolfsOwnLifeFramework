# 10 — Неделя: 7 дней + week picker

**What to build:** Пользователь открывает Неделю: таблица пн–вс × 15 минут, выбор недели, те же правила кликов/confirm что на Сегодня, выбор Дела или ad-hoc, быстрое создание Дела не уходя далеко.

**Blocked by:** 08 — Подтверждение плана + правила past/future

**Status:** ready-for-human

- [x] Week range API + UI grid 7 columns
- [x] Week picker (ISO week, Monday start)
- [x] Place/clear/confirm работают на неделе
- [x] Quick-create Дело из контекста недели
- [x] API-тест week bounds в timezone пользователя

## Notes

Branch: `feature/10-week-grid`.

### API

```
GET /api/v1/time-entries/week
  ?date=YYYY-MM-DD            # week containing date
  ?isoYear=2025&isoWeek=1     # pick by ISO week-year
  (default)                   # current week in user TZ

→ {
  isoYear, isoWeek,
  weekStart,            # Monday YYYY-MM-DD
  weekEndExclusive,     # next Monday YYYY-MM-DD
  rangeStart, rangeEnd, # LDT half-open [Mon 00:00, next Mon 00:00)
  timezone,
  days: [{ date, weekday }],  # 7 days MONDAY..SUNDAY
  entries: [ TimeEntryResponse… ]
}
```

Mutations reuse existing endpoints: PUT/DELETE time-entries, confirm, confirm-all, ensure-sleep (week `from`/`to`).

### UI

- `WeekView.vue` route `/week`, nav «Неделя» after «Сегодня»
- 7 columns × 15-min rows; night hide/show + ensure-sleep on load
- Same click rules as Today; «Подтвердить все» for week range
- Picker: Дело / ad-hoc / **Новое Дело** (quick-create via POST /delos)

### Tests

`TimeEntryApiIT`: `week_bounds_are_iso_monday_to_next_monday_exclusive`, `week_default_is_current_iso_week_in_user_timezone`, `week_by_iso_year_and_week_number`, `week_rejects_invalid_iso_week`, `week_unauthenticated_rejected`.
