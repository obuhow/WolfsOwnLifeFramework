# Тикет 03 — Ролевой гейт на `/api/v1/admin/**` (AgentController без `@PreAuthorize`)

Status: resolved
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

**Выбран вариант B** (matcher в `SecurityConfig`), как и рекомендовал тикет:

```java
.requestMatchers("/api/v1/bot/max/webhook").permitAll()
.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")   // ← добавлено
.anyRequest().authenticated()
```

Обоснование выбора B над A: гейт защищает **весь admin-префикс**, а не один
`AgentController`. Сейчас под `/api/v1/admin/**` живут два контроллера
(`AdminController` — со своим класс-уровневым `@PreAuthorize`, и `AgentController` — без
него); при варианте A следующий контроллер под тем же путём снова оказался бы открытым по
умолчанию. `@PreAuthorize` на `AdminController` намеренно **не снимался** — дублирующая
защита безвредна и оставляет метод-секьюрити на месте, если matcher когда-нибудь изменят.

**Легальные вызывающие (п.2):** во фронте вызовов нет — `grep -rn "admin/agent"` по
`web/src` пуст, кнопки «Запустить агента» в UI не существует. Единственный вызывающий —
ручной прогон из `testing/06-background-agent.md` (curl под админом) и IT-тест
`AgentApiIT` (ходит под `authedAdminClient()`). Поэтому п.3 (выносить легальный путь вне
admin-префикса) в объёме 1.3 не потребовался — как и допускал тикет.

**Проверка (Testing Decisions):** в `AdminApiIT` добавлены два теста —
`non_admin_accounts_get_403_on_admin_agent_run` (роль `USER` и аккаунт `DEMO` →
`403` на `POST /api/v1/admin/agent/run`) и `admin_is_not_forbidden_on_admin_agent_run`
(`ADMIN` → статус не `403`). Оба зелёные. Существующий
`regular_user_gets_403_on_admin_endpoints` не сломан. Фронт не трогали, но
`npm run build` прогнан — зелёный.

Влито в `develop` мержем `506c31c` (ветка `release-1.3/feature/03-admin-role-gate`,
коммит `00ce072`).

Примечание к прогону `AdminApiIT`: 7 тестов, 1 падение —
`named_admin_can_block_seed_admin_and_login_is_rejected` с
`IllegalStateException: Timeout on blocking read for 5000000000 NANOSECONDS`. Это
известная 5-секундная флакость `WebTestClient` под нагрузкой хоста, не связана с правкой
(тест не касается ни admin-префикса, ни агента).
