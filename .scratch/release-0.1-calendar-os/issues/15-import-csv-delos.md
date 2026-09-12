# 15 — Импорт CSV → Дела (+ опция в бэклог)

**What to build:** Пользователь один раз загружает CSV задач и получает Дела. Опционально добавляет их в Бэклог текущей недели. Без live-sync.

**Blocked by:** 06 — Дела; 11 — Бэклог недели

**Status:** ready-for-agent

- [ ] Upload CSV endpoint + documented column schema
- [ ] Creates Дела for current user
- [ ] Optional flag → current week backlog
- [ ] UI entry point
- [ ] API-тест import then GET delos/backlog
