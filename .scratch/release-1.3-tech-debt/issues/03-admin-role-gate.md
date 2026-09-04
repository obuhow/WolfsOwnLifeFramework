# Тикет 03 — Ролевой гейт на `/api/v1/admin/**` (AgentController без `@PreAuthorize`)

Status: needs-triage
Blocked by:
Type: task
Закрывает: `bugs/03-admin-agent-endpoint-missing-role-gate.md` (Б-3)

## Вопрос

Закрыть дыру: эндпойнт `/api/v1/admin/agent/run` (`AgentController`) доступен **любому
аутентифицированному**, хотя путь — admin. Ущерб: расход LLM-квоты по запросу гостя на
стенде с `wolf.llm.enabled=true`, вводящий в заблуждение путь, несоответствие остальным
админ-эндпойнтам.

## Доказательство контекста

- `AgentController.java` — `@RequestMapping("/api/v1/admin/agent")` + `@PostMapping("/run")`,
  **без `@PreAuthorize`**. В проекте `@PreAuthorize("hasRole('ADMIN')")` есть только на
  `AdminController.java:38`, `InviteController.java:41`, `InstanceController.java:53`.
- `SecurityConfig.java:35-47` — matcher'а на `/api/v1/admin/**` нет: только
  `.anyRequest().authenticated()`.
- `AdminController` (`/api/v1/admin/users`) для сравнения закрыт класс-уровневым
  `@PreAuthorize` — у `AgentController` его нет.

## Что сделать

1. **Закрыть гейт одним из способов** (выбрать при работе, обосновать в `## Answer`):
   - вариант A: `@PreAuthorize("hasRole('ADMIN')")` на `AgentController` (класс-уровень,
     как у `AdminController`);
   - вариант B: matcher `.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")` в
     `SecurityConfig` — закрывает сразу весь admin-префикс от будущих забытых контроллеров.
   Предпочтителен B (защита префикса целиком), при A обосновать.
2. **Проверить легальных вызывающих:** найти в `web/src` вызовы `/admin/agent/run` и
   убедиться, что кнопка доступна только админу (или её нет — тогда просто факт).
3. **Не оставить UI без ручного прогона для владельца** — если кнопка «Запустить агента»
   есть и она нужна владельцу-не-админу, вынести легальный путь вне admin-префикса
   отдельным решением (в объёме 1.3 это не требуется, если вызовов нет — только закрыть дыру).

## Testing Decisions

- Демо-гость (`DEMO`) и пользователь роли `USER` на `POST /api/v1/admin/agent/run` → `403`.
- Пользователь роли `ADMIN` на том же пути → не `403` (поведение дальше не проверяем —
  может требовать LLM; достаточно статуса не-403 либо прежнего кода).
- `npm run build` зелёный (если трогали фронт).

## Out of Scope

- Легальный «ручной прогон агента для текущего пользователя» вне admin-префикса — он
  понадобится релизу 1.7 (кнопка «Смоделировать ночь», wow-механика Ф2) и заводится там.
- Изменение логики `AgentJob`/`AgentService`.

## Answer

_(заполняется при выполнении: выбранный вариант п.1, найденные вызывающие, статус проверки.)_
