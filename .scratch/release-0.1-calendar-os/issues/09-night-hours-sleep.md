# 09 — Ночные часы + авто-«Сон»

**What to build:** Пользователь настраивает Ночные часы, скрывает/показывает ночные строки сетки. В пустые ночные ячейки подставляется Дело «Сон» (seed на пользователя); ручная другая запись override. Тогл «Показать ночные часы» на Сегодня/Неделе.

**Blocked by:** 03 — Настройки Пользователя; 07 — Запись времени + сетка «Сегодня»

**Status:** ready-for-human

- [x] Seed/ensure Дело «Сон» per user
- [x] Autofill sleep only on empty night cells for the viewed day/week load or explicit ensure endpoint — поведение стабильно и задокументировано
- [x] Manual entry overrides sleep
- [x] Hide/show night rows in UI
- [x] API/UI тесты на autofill и override

## Comments

### Implementation (feature/09-night-hours-sleep)

- `POST /api/v1/time-entries/ensure-sleep` `{ from, to }` — fills empty night slots with Дело «Сон»; never overwrites; idempotent.
- Night window half-open on daily cycle (`nightStart`/`nightEnd` from user settings); wraps midnight when start > end.
- Status on autofill: past → DONE, future → PLANNED.
- UI `TodayView`: load settings + ensure-sleep on day load; toggle «Показать ночь» (localStorage `wolf_show_night_hours`, default hide).
- IT: `TimeEntryApiIT` ensure_sleep_* (5 cases). Week grid (ticket 10) reuses same endpoint.
