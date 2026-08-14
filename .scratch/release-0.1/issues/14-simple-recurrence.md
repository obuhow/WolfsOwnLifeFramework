# 14 — Правило повторения (простое)

**What to build:** На странице Дела Пользователь задаёт простое Правило повторения (дни недели, опционально окно времени) и применяет на горизонт (напр. 4 недели): создаются будущие Записи времени `planned`. Уже `done` в прошлом не переписываются.

**Blocked by:** 06 — Дела; 07 — Записи времени

**Status:** ready-for-human

- [x] Store rule fields on Дело (or side entity)
- [x] Apply command creates future planned entries only within horizon
- [x] Does not mutate historical done entries
- [x] UI on Дело page
- [x] API-тесты horizon и non-rewrite

## Implementation notes

Branch: `feature/14-simple-recurrence`

- Rule stored on `delo`: `recurrence_weekdays`, `recurrence_window_start`, `recurrence_window_end` (Flyway V9). Horizon is apply-time only.
- `POST /api/v1/delos/{id}/apply-recurrence` persists the rule and creates future `PLANNED` intervals. Occupied slots (including `DONE`) are skipped. Empty window → 09:00–09:15.
- `GET /api/v1/delos/{id}` returns the stored rule.
- UI: section «Правило повторения» on `DeloDetailView.vue`.
- Tests: `RecurrenceApiIT` (horizon, non-rewrite, store, unauthenticated).
