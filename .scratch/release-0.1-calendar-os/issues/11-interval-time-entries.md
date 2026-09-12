# 11 — Интервалы Записи времени + Конец дня

## Summary
Запись времени — непрерывный интервал `[start_at, end_at)` (кратно 15 мин), не набор слотов.
Сетка: край блока ±15 мин, середина — разрез; авто-Сон одним интервалом (по умолчанию 02:00–09:00).
Настройка **Конец дня** (`day_end`) задаёт логические сутки (дела после полуночи остаются в том же дне сетки).

## Acceptance
- [x] `time_entry.end_at` — реальная граница интервала; 1 Сон = 1 row 02:00–09:00
- [x] `POST /time-entries/grid-click` — shrink / extend / split / place / NEED_PICKER
- [x] `PUT` принимает optional `endAt`; overlaps вытесняются
- [x] `ensure-sleep` — один интервал на логический день, не overwrite
- [x] settings: `dayEnd`, `defaultSleepEnd`
- [x] Today + Week UI: интервалы, grid-click, подпись `Сон 02:00–09:00`
- [x] IT: shrink/extend/split + day_end bounds + ensure-sleep interval
- [x] docs / skill

## Branch
`feature/11-interval-time-entries`
