# Planning & Priorities: Goal, Project, ProjectDependency, Backlog, WeekBacklog

Type: task
Status: resolved
Blocked by: 01

## Question

Мигрировать `GoalController` (292 строки, 12 вложенных Response/Request-классов — крупная фича), `ProjectController` (437 строк), `ProjectDependencyController`, `BacklogController`, `WeekBacklogController`. `GoalFactService`, `ResourceCascadeService`, `PlanDistributionService` — уже существующие сервисы; тикет решает, поглощать их в новый `XxxService` фичи или оставлять отдельными (см. `Not yet specified` в map.md).

Definition of done: см. map.md; `*ApiIT` (`GoalApiIT`, `GoalMetricIncrementApiIT`, `ProjectApiIT`, `ResourceCascadeApiIT`, `PlanDistributionApiIT`, `BacklogApiIT`) зелёные без правок.

## Answer

Кластер Planning & Priorities мигрирован по паттерну ADR 0005.

### Что сделано

- **GoalController** (292 стр., 12 вложенных DTO) → `GoalService`. DTO — records в
  `goal/dto/`: `GoalResponse`, `GoalDetailResponse`, `ProjectResponse`, `BudgetResponse`,
  `MetricResponse`, `FactResponse`, `CreateGoalRequest`, `UpdateGoalRequest`,
  `PriorityRequest`, `BudgetRequest`, `MetricRequest`, `IncrementRequest`.
- **ProjectController** (437 стр.) → `ProjectService`. DTO в `project/dto/`:
  `ProjectResponse`, `ProjectDetailResponse`, `DeloLink`, `CreateProjectRequest`,
  `UpdateProjectRequest`, `PlanShiftPreviewRequest`, `PlanDistributionRequest`.
- **ProjectDependencyController** → `ProjectDependencyService`. DTO:
  `AddDependencyRequest`, `DependenciesResponse`, `ProjectSummary` (граф/DFS-поиск
  цикла переехал в сервис целиком).
- **BacklogController** → `BacklogService`. DTO в `backlog/dto/`: `Request`, `Response`,
  `MoveRequest`, `ScopeParam`.
- **WeekBacklogController** → `WeekBacklogService`. DTO: `WeekBacklogResponse`, `DeloResponse`.

### Решение по вопросу тикета (поглощать ли существующие сервисы)

**Не поглощать.** `GoalFactService`, `ResourceCascadeService`, `PlanDistributionService`,
`FactAggregateService` оставлены отдельными и стали **зависимостями** новых сервисов фичи —
как и предписывает map.md («существующий сервис становится зависимостью нового тонкого
Service, а не сливается»). Это подтверждено практикой: у каждого из них своя зона
ответственности и свои тесты (`ResourceCascadeApiIT`, `PlanDistributionApiIT`), слияние
только раздуло бы `ProjectService`.

Типы `ResourceCascadeService.Preview` и `PlanDistributionService.DistributionResult`
остаются публичными типами своих сервисов и возвращаются наружу как есть — это не
вложенные DTO контроллера, переносить их в `dto/` не требуется.

### Тесты

Зелёные (изолированно, Testcontainers): `GoalApiIT`, `GoalMetricIncrementApiIT`,
`ProjectApiIT`, `ResourceCascadeApiIT`, `PlanDistributionApiIT`, `BacklogApiIT`,
а также смежные, задетые переездом DTO: `AggregateApiIT`, `DeloApiIT`, `GanttApiIT`,
`MorningDigestApiIT`, `NoteApiIT`.

`AgentApiIT` и `NotesAssistantApiIT` падают — **предсуществующий баг, не регрессия
тикета**: проверено откатом изменений (`git stash`) на чистом `develop`, падение
воспроизводится идентично. Причина: `Driver org.h2.Driver claims to not accept jdbcUrl:
jdbc:postgresql://...` — конфликт H2-драйвера из их тестовой конфигурации с
Testcontainers-postgres. Требует отдельного тикета.

### Механические правки тестов (по ADR 0005, «Осознанные исключения»)

Переезд DTO задел 12 тест-файлов, включая соседние кластеры. Правки только механические:
FQN типа (`ProjectController.ProjectResponse` → `project.dto.ProjectResponse`),
record-accessor вместо JavaBean-геттера (`getId()` → `id()`) и конструирование через
канонический конструктор вместо `@Data`-сеттеров. Проверяемое поведение не менялось.

Две ловушки, которые стоит знать при следующих кластерах:
- **Одноимённые DTO в разных пакетах**: `ProjectResponse` есть и в `goal/dto`, и в
  `project/dto`. Wildcard-импорт обоих даёт `reference to ProjectResponse is ambiguous` —
  нужны явные импорты или FQN (`GoalApiIT`, `MorningDigestApiIT`).
- **Одноимённые переменные разных типов**: в `AggregateApiIT`/`ProjectApiIT` переменные
  `detail`/`created` в разных тестах имеют то мой DTO, то чужой (`DeloDetailResponse`,
  `LifeAreaResponse`). Массовая замена `getX()` → `x()` их ломает: нужно отслеживать
  объявленный тип переменной (или переименовать переменную), иначе правка «переедет» на
  чужой класс.

### Совместимость

В `CreateProjectRequest` добавлены два convenience-конструктора (`(lifeAreaId, title)` и
`(lifeAreaId, title, startDate, endDate, totalPlanHours)`), в `UpdateProjectRequest`
сохранён 8-аргументный — они покрывают вызовы из тестов соседних кластеров и позволили
не переписывать их helper'ы целиком.

