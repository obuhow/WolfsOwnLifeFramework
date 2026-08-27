# WOLF

## Agent skills

### Issue tracker

Issues live as local markdown under `.scratch/<feature>/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-role vocabulary (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: root `CONTEXT.md` + `docs/adr/`. See `docs/agents/domain.md`.

## Active wayfinder map

`.scratch/wayfinder-release-0.9-layered-arch/map.md` — релиз 0.9: миграция `wolf-api` на слоистую архитектуру (тонкий MVCS + точечные Ports&Adapters).

Предыдущие карты: `.scratch/wayfinder-releases-05-07/map.md` (релизы 0.5–0.7); `.scratch/wayfinder-wolf/wolf-life-os-map.md` (релизы 0.1–0.2).

## Текущая архитектура API (release 0.9)

`wolf-api` (`ru.wolf.api.*`, package-by-feature) мигрирован на слоистую архитектуру. Это
**текущее состояние** кодовой базы (зафиксировано сквозным аудитом, тикет 11), не только
ADR-решение — см. `docs/adr/0005-layered-architecture-mcv.md` за обоснованием.

- **Контроллер** (`*Controller`) — только HTTP: маршрутизация, `@Valid`, чтение
  пути/параметров, маппинг тела в DTO, обёртка в `ResponseEntity`. **Не инжектит
  `Repository`** (сквозной `grep -rl "private final.*Repository" --include='*Controller.java'`
  в `api/src/main/java` пуст). Исключения вне контура: `HealthController` (нет БД/бизнес-логики,
  инжектит `DataSource`) и `WolfApiApplication`.
- **Сервис** (`*Service`) — бизнес-логика и `@Transactional`/`@Transactional(readOnly = true)`;
  резолвит текущего `User` по `authentication.getName()` через `UserRepository`; бросает те же
  доменные исключения, что ранее бросал контроллер (ловятся `GlobalExceptionHandler` по типу,
  HTTP-контракт не меняется). Без веб-аннотаций (`@RestController`/`@RequestMapping`/...).
- **DTO** — Java **records** в `ru.wolf.api.<feature>.dto`, не `@Data`-классы. Маппинг —
  ручной статический фабричный метод **на самом record** (`XxxResponse.from(entity)`);
  `from` строит flat-ответ (соседние `@ManyToOne` → `id`/`title`, без ленивых графов).
  Имена: `Create<X>Request`/`Update<X>Request` (запросы), `<X>Response` (ответы). Валидация
  (`@NotBlank`/`@Size`) — на полях record. 26 пакетов `dto/`, 130+ record-классов.
- **Порты и адаптеры** — точечно, только где два адаптера: интерфейс `XxxPort` в пакете фичи,
  реализации `JpaXxxAdapter`/`HttpXxxAdapter`/`FakeXxxAdapter`. Образец: `note/assistant`
  (`AssistantPort` + `FakeNotesAssistantAdapter` `@Profile("test")` / `HttpNotesAssistantAdapter`
  `@Profile("!test")`). Для `datasync` порт не вводился (один реальный адаптер workbook).
- **Существующие сервисы не поглощаются** — становятся зависимостями нового тонкого сервиса
  фичи (`AgentRunLogService`, `RecurrenceService`, `GoalFactService`, `FactAggregateService`,
  `SyncExternalIdService`, `PlanDistributionService`, `GanttForecastService` и др. остаются
  отдельными сфокусированными сервисами).
- **Тесты** — преимущественно чёрный ящик `*ApiIT` через HTTP (контракт не меняется при
  рефакторинге); при переезде DTO из контроллера в `dto/` правятся только FQN и
  `getId()`→`id()` (record-accessor). DB-free unit-тесты на затронутую логику там, где authored.

