# 08 — Зависимости между Проектами (A блокирует B, защита от циклов)

**What to build:** `project_dependency` — направленная связь "Проект A блокирует Проект B" (B не может начаться, пока A не завершён). Защита от циклов: при добавлении связи проверка, что она не создаёт цикл (DFS по графу). API: `POST/DELETE /projects/{id}/dependencies`, `GET /projects/{id}/dependencies` (blocked-by и blocks). UI: блок "Зависимости" на странице Проекта — список "ждёт" и "блокирует", добавление через autocomplete по проектам. Циклы отклоняются с 409 и пояснением пути цикла.

**Blocked by:** None — can start immediately.

**Status:** resolved

**Implementation frontier:** ticket 08 — backend `project_dependency` graph/API and the «Зависимости» block on the Project detail page. Forecast/cascade behavior from tickets 09–10 is deferred.

- [x] Таблица `project_dependency` (blocker_id, blocked_id, user_id) + уникальный индекс
- [x] `POST /api/v1/projects/{id}/dependencies {blockerId}` — добавить "blockerId блокирует id"
- [x] Проверка цикла при добавлении (DFS), 409 с путём цикла в ответе
- [x] `DELETE /api/v1/projects/{id}/dependencies/{blockerId}`
- [x] `GET /api/v1/projects/{id}/dependencies` — {blockedBy: [...], blocks: [...]}
- [x] UI: блок Зависимости на странице Проекта (два списка + добавление)
- [x] API test: A блокирует B, попытка B→A → 409 с пояснением
- [x] API test: цепочка A→B→C, попытка C→A → 409
- [x] API test: удаление зависимости → повторное добавление C→A разрешено

## Answer

Реализовано в commits `cccd688` и `6e42774`, ветка `feature/08-project-dependencies`.

Verification evidence:

- `./gradlew test --tests '*.ProjectApiIT' --no-daemon` — BUILD SUCCESSFUL; 17 tests passed, including direct A→B→A, chain cycle, delete/re-add, and chain rewire cases.
- Sequential API class run: 18/19 classes passed. The only unrelated failure was the pre-existing CSV import assertion in `DeloApiIT#import_csv_creates_delos_and_adds_them_to_current_week_backlog` at line 402; all dependency and project tests passed. The ordinary full run failed earlier because Testcontainers PostgreSQL connections were lost under the long shared-container suite.
- `./gradlew bootJar --no-daemon` — BUILD SUCCESSFUL.
- `npm run build` — BUILD SUCCESSFUL; served isolated web container exposed the new hashed JS/CSS assets and the `Зависимости` marker.
- Isolated Compose stack on ports `18080/18082/15434`: API health returned `{"status":"UP"}` and Flyway applied migration v19.
- Authenticated API smoke: login succeeded; A→B→C dependency chain, cycle rejection with HTTP 409, delete and re-add all verified.
- Headless Chrome CDP DOM smoke: authenticated `/projects/1` rendered the `Зависимости` surface, blocker input, and `ЖДЁТ`/`БЛОКИРУЕТ` lists. The autocomplete now uses `Название (#id)` labels so duplicate project titles remain unambiguous. Desktop Chrome browser harness was unavailable because its remote-debugging permission was not granted; the headless CDP check provided the real browser DOM evidence instead.
