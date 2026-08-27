# Agent Jobs: Agent, AgentRunLog

Type: task
Status: claimed
Blocked by: 01

## Question

Мигрировать `AgentController`. `AgentRunLogService` — уже существующий сервис, решить, поглощается или остаётся. Небольшой кластер, ~3 файла.

Definition of done: см. map.md; `AgentApiIT` зелёный без правок.

## Answer

Реализована миграция кластера `Agent` в thin MVCS без изменения HTTP-маршрутов и контрактов.

- `AgentController` очищен от `UserRepository` и бизнес-логики: теперь инжектит только `AgentService` и делегирует `run(username)`. Текущий пользователь резолвится в сервисе по `authentication.getName()` (паттерн `IdeaService.currentUser`).
- Добавлен `AgentService` (тонкий сервис фичи): резолвит `User` через `UserRepository`, вызывает `agentJob.runForUser(user)`, оборачивает `AgentJob.AgentRunResult` в DTO. `AgentRunLogService` **не поглощён** — оставлен отдельным сфокусированным сервисом-зависимостью (по правилу карты для существующих сервисов; он уже «глубокий» и не несёт бизнес-логики контроллера).
- Публичный `RunResponse` вынесен из контроллера в `ru.wolf.api.agent.dto.AgentRunResponse` как Java **record** с фабрикой `from(AgentJob.AgentRunResult)`; `AgentApiIT` поправлен механически (FQN + record-accessor) без изменения проверяемого поведения.

Definition of done: `clean compileJava compileTestJava` — BUILD SUCCESSFUL (JDK 21, 36s, локально на хосте — docker-daemon был перегружен); структурный скан: `AgentController` без поля `Repository`, `AgentService` без веб-аннотаций/импортов/ResponseEntity, DTO — record; сквозной grep `private final .*Repository` по всем `*Controller.java` — **0 совпадений** (финальное состояние карты 0.9). Полный `AgentApiIT` отложен до release-gate 0.9 согласно принятой lightweight стратегии.
