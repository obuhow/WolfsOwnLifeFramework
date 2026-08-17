# 16 — Импорт ICS → Записи времени

**What to build:** Пользователь один раз загружает ICS; события становятся Записями времени на 15-минутной сетке (floor/split длинных). При необходимости создаются Дела по summary. Конфликты ячеек обрабатываются предсказуемо (задокументировать: skip / overwrite last — выбрать и зафиксировать в acceptance при реализации, default: не затирать done без явного флага).

**Blocked by:** 07 — Запись времени + сетка «Сегодня»

**Status:** wontfix

- [ ] Upload ICS endpoint
- [ ] Maps into 15-min cells in user timezone
- [ ] Creates/links Дела by summary when useful
- [ ] UI entry point
- [ ] API-тест import then GET time entries range
