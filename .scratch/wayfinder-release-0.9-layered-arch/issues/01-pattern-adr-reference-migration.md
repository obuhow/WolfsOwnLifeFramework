# Паттерн, ADR и референсная миграция

Type: task
Status: resolved
Blocked by:

## Question

Зафиксировать точный, воспроизводимый паттерн тонкого MVCS + точечных портов (вариант D, см. `map.md`) как ADR, и провести миграцию одной небольшой референсной фичи (`Idea` — `IdeaController`, ~4 файла, уже есть `IdeaAlreadyPromotedException`) по этому паттерну, чтобы все последующие тикеты копировали проверенный образец, а не изобретали вариации.

Конкретно нужно решить и зафиксировать примерами кода:

- Точное имя и расположение `dto/`-пакета, naming convention для record'ов (`CreateGoalRequest` → куда, `GoalResponse` → куда).
- Как выглядит статический фабричный метод маппинга на record (`GoalResponse.from(Goal goal)` vs конструктор vs отдельный `GoalMapper`).
- Куда переезжает `@Transactional` — остаётся на методах контроллера или переезжает в Service (рекомендация: в Service).
- Как Service получает текущего `User` — остаётся ли `Authentication authentication` параметром контроллера, который резолвит User и передаёт в Service, или Service сам берёт из `SecurityContextHolder`.
- Где живут доменные исключения (`DuplicateGoalPriorityException` и т.п.) — остаются как есть рядом с моделью.
- Публикуется как `docs/adr/0005-layered-architecture-mccv.md` (или следующий свободный номер).

## Answer

Паттерн зафиксирован в ADR `docs/adr/0005-layered-architecture-mcv.md` (вариант D — тонкий
MVCS + точечные порты) и применён на референсной фиче `Idea`.

### Решённые вопросы из Question

- **dto/-пакет**: `ru.wolf.api.<feature>.dto`, records. Запросы `Create<X>Request`,
  `Update<X>Request`, `Promote<X>Request`; ответы `<X>Response`; ответы действий
  `<X><Action>Response` (`PromoteResponse`).
- **Маппинг**: статический фабричный метод **на самом record** — `IdeaResponse.from(Idea)`.
  Без MapStruct, без отдельного `XxxMapper`.
- **@Transactional**: переехал в `IdeaService` (на методах; `list`/`get` — `readOnly = true`).
- **Текущий User**: контроллер передаёт `authentication.getName()` в сервис; сервис резолвит
  `User` через `UserRepository` (образец `MorningDigestController`/`MorningDigestService`).
  Контроллер не инжектит `Repository` вообще.
- **Доменные исключения** (`IdeaAlreadyPromotedException`) — остались в пакете фичи рядом с
  моделью; `GlobalExceptionHandler` ловит по типу → 409 сохранён.

### Что сделано

- `IdeaController` стал тонким (HTTP/валидация/маршрутизация), без `Repository` (DoD п.1 ✓).
- `IdeaService` несёт всю бизнес-логику + `@Transactional` (DoD п.2 ✓).
- `CreateIdeaRequest`/`UpdateIdeaRequest`/`PromoteIdeaRequest`/`PromoteResponse`/`IdeaResponse`
  — records в `ru.wolf.api.idea.dto` (DoD п.3 ✓).
- Тесты: `IdeaApiIT` 5/5, `MorningDigestApiIT` 2/2 зелёные без изменения проверяемого
  поведения (DoD п.4 ✓). Механически поправлены только FQN типов и `getId()`→`id()`
  (record-accessor) — см. раздел «Осознанные исключения» в ADR.
- `grep -rln "private final.*Repository" --include='*Controller.java' api/src/main/java/ru/wolf/api/idea/`
  — **пусто** (DoD п.4 grep-критерий по карте ✓).

### Референс для копирования

Следующие тикеты (02–10) копируют структуру `ru.wolf.api.idea`:
`XxxController` (только HTTP) → `XxxService` (`@Service`, `@Transactional`, резолв User по
username) → `dto/Xxx*.java` (records) + репозитории. Файлы: `IdeaController.java`,
`IdeaService.java`, `dto/*.java`, `Idea.java`, `IdeaRepository.java`, `IdeaAlreadyPromotedException.java`.

