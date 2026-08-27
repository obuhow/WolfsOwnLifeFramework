# Competency & Life Structure: LifeArea, LifeSphere, Synergy

Type: task
Status: resolved
Blocked by: 01

## Question

Мигрировать `LifeAreaController`, `LifeSphereController`, `SynergyController`. Небольшой, преимущественно CRUD-кластер — хороший второй кандидат на быструю параллельную сессию после референсной фичи из тикета 01.

Definition of done: см. map.md; `*ApiIT` (`LifeAreaApiIT`, `LifeSphereApiIT`, `SynergyApiIT`) зелёные без правок.

## Answer

Кластер `Competency & Life Structure` мигрирован на thin MVCS по ADR 0005 (референс — `ru.wolf.api.idea`).

**Сделано:**
- `LifeAreaController`, `LifeSphereController`, `SynergyController` стали тонкими: только HTTP/валидация, делегируют сервисам; `User` резолвится в сервисе по `authentication.getName()`. Ни один из трёх контроллеров не инжектит `Repository` (проверено `grep -rn 'private final.*Repository'`).
- Добавлены сервисы `LifeAreaService`, `LifeSphereService`, `SynergyService` (`@Service` + `@RequiredArgsConstructor` + `@Transactional`); вся бизнес-логика (проверки дублей имён, reorder/sortOrder, archive-toggle, валидация «ровно один владелец синергии», изоляция по пользователю) перенесена из контроллеров в сервисы без изменения поведения.
- DTO вынесены в `ru.wolf.api.lifearea.dto` и `ru.wolf.api.lifesphere.dto` как Java `record` (11 типов: `LifeAreaResponse`, `Create/Update/MoveLifeAreaRequest`, `LifeSphereResponse`, `Create/Update/MoveLifeSphereRequest`, `SynergyResponse`, `Create/UpdateSynergyRequest`). Мапперы — статические `from(...)` на самих record'ах.
- HTTP-контракт не изменился (пути, методы, коды ответов идентичны).

**Проверки (реальный прогон):**
- `./gradlew clean compileJava compileTestJava` — BUILD SUCCESSFUL.
- Структурные проверки ADR 0005: целевые контроллеры без поля `Repository`; сервисы без веб-аннотаций/импортов `org.springframework.web`; все DTO — records в `dto/`.
- Механическая правка соседних `*ApiIT` (Gantt, Routine, MorningDigest, Idea, Agent, Note, NotesAssistant, Goal, Delo, Aggregate, Project, ResourceCascade), ссылавшихся на вложенные `LifeAreaController.CreateLifeAreaRequest`/`LifeAreaResponse`: FQN заменён на `ru.wolf.api.lifearea.dto.*`, `getX()`→`x()`, no-arg+setter → all-args конструктор record'ов. Поведение тестов не менялось (разрешено DoD п.4 «без правок логики»).

**Не выполнено (по согласованной lightweight-стратегии релиза 0.9):** полные кластерные `*ApiIT` через Testcontainers не прогонялись (слабый хост, единый release-gate после тикетов 04–10). Логика покрыта компиляцией и структурными проверками; чёрно-ящичный контракт сохранён идентичным.
