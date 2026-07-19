# 14 — Правило повторения (простое)

**What to build:** На странице Дела Пользователь задаёт простое Правило повторения (дни недели, опционально окно времени) и применяет на горизонт (напр. 4 недели): создаются будущие Записи времени `planned`. Уже `done` в прошлом не переписываются.

**Blocked by:** 06 — Дела; 07 — Записи времени

**Status:** ready-for-agent

- [ ] Store rule fields on Дело (or side entity)
- [ ] Apply command creates future planned entries only within horizon
- [ ] Does not mutate historical done entries
- [ ] UI on Дело page
- [ ] API-тесты horizon и non-rewrite
