# 08 — Зависимости между Проектами (A блокирует B, защита от циклов)

**What to build:** `project_dependency` — направленная связь "Проект A блокирует Проект B" (B не может начаться, пока A не завершён). Защита от циклов: при добавлении связи проверка, что она не создаёт цикл (DFS по графу). API: `POST/DELETE /projects/{id}/dependencies`, `GET /projects/{id}/dependencies` (blocked-by и blocks). UI: блок "Зависимости" на странице Проекта — список "ждёт" и "блокирует", добавление через autocomplete по проектам. Циклы отклоняются с 409 и пояснением пути цикла.

**Blocked by:** None — can start immediately.

**Status:** claimed

**Implementation frontier:** ticket 08 — backend `project_dependency` graph/API and the «Зависимости» block on the Project detail page. Forecast/cascade behavior from tickets 09–10 is deferred.

- [ ] Таблица `project_dependency` (blocker_id, blocked_id, user_id) + уникальный индекс
- [ ] `POST /api/v1/projects/{id}/dependencies {blockerId}` — добавить "blockerId блокирует id"
- [ ] Проверка цикла при добавлении (DFS), 409 с путём цикла в ответе
- [ ] `DELETE /api/v1/projects/{id}/dependencies/{blockerId}`
- [ ] `GET /api/v1/projects/{id}/dependencies` — {blockedBy: [...], blocks: [...]}
- [ ] UI: блок Зависимости на странице Проекта (два списка + добавление)
- [ ] API test: A блокирует B, попытка B→A → 409 с пояснением
- [ ] API test: цепочка A→B→C, попытка C→A → 409
- [ ] API test: удаление зависимости → повторное добавление C→A разрешено
