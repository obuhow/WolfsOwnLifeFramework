# WOLF 0.9 — Слоистая архитектура API — Wayfinder Map

## Destination

`wolf-api` мигрирован на согласованную слоистую архитектуру во всех фичах: **Controller → Service → Repository**, с явным `dto/`-слоем (Java records) вместо вложенных `@Data`-классов в контроллерах, и **ни один контроллер не инжектит `Repository` напрямую**. Точечно, там где уже есть или скоро появится второй адаптер (`note/assistant`, `datasync`), слой формализован как **порт/адаптер** (интерфейс `XxxPort` + реализации), а не просто Service.

Карта пройдена, когда: (а) описанный ниже паттерн задокументирован в ADR и применён на референсной фиче; (б) все фиче-кластеры мигрированы по нему; (в) все существующие `*ApiIT` зелёные без изменения их кода (чёрный ящик через HTTP не меняется); (г) `grep -rl "private final.*Repository" --include='*Controller.java'` в `src/main/java` пуст (кроме осознанно исключённых, см. Notes).

Это override правила «планируй, не делай»: тикеты этой карты **выполняют миграцию кода**, а не только описывают решение — релиз 0.9 сдаётся как рабочий рефакторинг, не как spec.md.

## Notes

- **Домен**: персональная Life OS, RU-интерфейс. Стек: Java 21 + Spring Boot 3.4 + Gradle, Vue 3, Postgres, JWT. Модуль `api/` (`ru.wolf.api.*`), package-by-feature.
- **Навыки каждой сессии**: `wolf-life-os`, `codebase-design` (глоссарий Module/Interface/Depth/Seam/Adapter), `domain-modeling` при спорных терминах.
- **Трекер**: локальный markdown (`docs/agents/issue-tracker.md`).
- **Предшествующая карта**: `.scratch/wayfinder-releases-05-07/map.md` (релизы 0.5–0.7).
- **Отправная точка (замер на момент чертежа)**: 39 контроллеров, 15 сервисов, 31 репозиторий, **0 файлов `dto/`** — почти все `Request`/`Response` живут как `@Data`-классы внутри контроллеров; **36 из 39 контроллеров** инжектят `Repository` напрямую и несут бизнес-логику (пример: `TimeEntryController` 847 строк, `GanttController` 587 строк). Тесты — преимущественно чёрный ящик `*ApiIT` через HTTP, что и даёт низкий риск рефакторинга: внутреннее устройство меняется, граница не меняется.
- **Выбранный вариант архитектуры (D — гибрид)**, зафиксирован в этом чертеже, не тикет:
  - Базовый паттерн — **тонкий MVCS**: `Controller` только HTTP/валидация/authentication → `Service` — бизнес-логика и `@Transactional` → `Repository`. Действует для всех фич без исключений, включая тривиальный CRUD (единообразие важнее экономии строк на простых фичах — агенту, работающему по одному тикету на фичу, нужен один узнаваемый паттерн, а не «где-то Service обязателен, где-то нет»).
  - **Ports & Adapters — точечно**, только где уже есть или явно предвидится второй адаптер (правило «один адаптер — гипотетический шов, два — реальный», см. `codebase-design`): `note/assistant` (`NotesAssistant` — уже порт с `FakeNotesAssistant`/`HttpNotesAssistant`, только формализовать по конвенции ниже) и `datasync` (форматы импорта/экспорта, легаси-нормализация воркбука).
  - **DTO**: Java **records** в подпакете `dto/` каждой фичи (`ru.wolf.api.<feature>.dto`), не `@Data`-классы. Маппинг — **ручной**, статический фабричный метод на самом record (`GoalResponse.from(Goal goal)`), без MapStruct и новых зависимостей.
  - **Порты**: интерфейс `XxxPort` в пакете фичи; реализации-адаптеры называются по роли (`JpaXxxAdapter`, `HttpXxxAdapter`, `FakeXxxAdapter`) — как уже сделано в `note/assistant`, эта фича не переименовывается, только используется как образец конвенции.
  - **Definition of done на тикет**: 1) контроллер фичи не содержит поле `Repository`; 2) вся бизнес-логика — в `Service` (или порт/адаптер, если применимо); 3) публичные Request/Response — records в `dto/`; 4) `clean compileJava compileTestJava` и структурные проверки проходят; 5) минимальные unit/smoke-проверки затронутой логики проходят; 6) полные `*ApiIT` отдельных кластеров не являются обязательным тикетным gate и выполняются единым release-gate после тикетов 04–10; 7) `GlobalExceptionHandler` и доменные исключения (`DuplicateGoalPriorityException` и т.п.) не переносятся без необходимости — остаются рядом с моделью, как сейчас.
  - **Исключение**: `HealthController` (нет обращения к БД/бизнес-логики) и `WolfApiApplication` не мигрируются — вне контура рефакторинга.
- **Порядок**: тикет 01 (паттерн + ADR + референсная миграция) **блокирует все фиче-кластеры** — так все параллельные сессии/агенты идут по одному проверенному образцу, а не изобретают вариации. Финальный тикет — сквозной аудит — блокируется всеми кластерами.
- **Границы фиче-кластеров ниже — ориентир, не жёсткий реестр.** Список контроллеров по каждому кластеру взят на момент чертежа; перед началом работы по тикету агент сверяет актуальный список `find src/main/java -name '*Controller.java'` для своей фичи — код мог измениться.

## Decisions so far

- **04 (Time Grid: TimeEntry, Calendar, Routine) — resolved.** `TimeEntryController`,
  `CalendarController` и `RoutineController` мигрированы на thin MVCS через
  `TimeEntryService`, `CalendarService` и `RoutineService`; `RecurrenceService` оставлен
  отдельным сервисом. DTO вынесены в feature-specific `dto/` и оформлены как records.
  Контроллеры не инжектят `Repository`; логика `NightHours`/`DayBounds` сохранена.
  `clean compileJava compileTestJava` и структурные проверки зелёные. Полные кластерные
  `ApiIT` перенесены в единый release-gate после тикетов 04–10 по согласованной lightweight
  стратегии тестирования.

- **01 (Паттерн, ADR и референсная миграция) — resolved.** Паттерн зафиксирован в
  `docs/adr/0005-layered-architecture-mcv.md` (вариант D: тонкий MVCS + точечные порты) и
  применён на `Idea`. Контроллер тонкий (без `Repository`), бизнес-логика + `@Transactional`
  в `IdeaService`, DTO — records в `ru.wolf.api.idea.dto` с маппером `IdeaResponse.from(Idea)`,
  текущий `User` резолвится в сервисе по `authentication.getName()`. Тесты `IdeaApiIT` (5/5)
  и `MorningDigestApiIT` (2/2) зелёные. `grep` по `private final.*Repository` в
  `api/src/main/java/ru/wolf/api/idea/*Controller.java` — пуст. Все тикеты 02–10 копируют
  структуру `ru.wolf.api.idea`. См. Answer тикета 01.
  - **Изменения в правилах трекера, всплывшие при 01**: DoD п.4 («*ApiIT без правок кода»)
    на практике требует механической правки — при переезде DTO из вложенных типов контроллера
    в `dto/` правятся только FQN типа и `getId()`→`id()` (record-accessor), проверяемое
    поведение не меняется. Зафиксировано в ADR как «Осознанные исключения».

- **02 (Identity & Access: Auth, User, Admin, Invite, Onboarding) — resolved.** Мигрирован
  весь кластер: 5 тонких контроллеров (без `Repository`) → сервисы `AuthService`,
  `UserSettingsService`, `AdminService`, `OnboardingService` (+ расширен `InviteService`
  методом `currentUser`); DTO — records в `ru.wolf.api.<feature>.dto`. `AuthManager`/`JwtUtil`
  оставлены в `AuthController` (Security-инфраструктура, не Repository). Admin-отказы
  (само-блок, последний админ, несовпадение username) бросаются как `IllegalArgumentException`
  → 400 через `GlobalExceptionHandler`. Тесты кластера зелёные изолированно
  (`AuthApiIT`, `UserModelApiIT`, `UserSettingsApiIT`, `AdminApiIT`, `RegistrationApiIT`,
  `OnboardingApiIT`); `OnboardingApiIT` поправлен механически (FQN + конструктор records).
  Всплыло: при пакетном прогоне IT на слабом хосте Testcontainers-postgres flaky
  (`CannotCreateTransactionException`/таймаут логина) — лечится запуском по одному классу
  и лимитом JVM-памяти (`-Xmx768m`). Контракт HTTP не изменился.

- **03 (Planning & Priorities: Goal, Project, ProjectDependency, Backlog, WeekBacklog) — resolved.**
  5 тонких контроллеров → `GoalService`, `ProjectService`, `ProjectDependencyService`,
  `BacklogService`, `WeekBacklogService`; 28 DTO-records в `goal/dto`, `project/dto`,
  `backlog/dto`. **Закрыт вопрос из Not yet specified**: существующие сервисы
  (`GoalFactService`, `ResourceCascadeService`, `PlanDistributionService`,
  `FactAggregateService`) **не поглощаются** — становятся зависимостями сервисов фичи;
  их публичные типы (`Preview`, `DistributionResult`) возвращаются наружу как есть.
  Тесты кластера и смежных зелёные изолированно. `AgentApiIT`/`NotesAssistantApiIT`
  падают из-за **предсуществующего** бага (H2-драйвер vs Testcontainers-postgres) —
  проверено на чистом `develop` откатом изменений; нужен отдельный тикет.
  Всплыло при массовой правке тестов: (1) одноимённые DTO в разных пакетах
  (`ProjectResponse` в `goal/dto` и `project/dto`) ломают wildcard-импорт — нужны явные
  импорты; (2) одноимённые переменные разных типов (`detail`, `created`) требуют
  отслеживать объявленный тип при замене `getX()`→`x()`, иначе правка задевает чужой класс.

## Not yet specified

- **Нужен ли `ImportXlsxPort`/формальный порт для datasync и легаси-нормализации workbook**, или там достаточно обычного Service (в отличие от `note/assistant`, где два реальных адаптера уже существуют, у `datasync` пока один формат хранения) — решится внутри тикета 06 при погружении в код.
- **Судьба `RecurrenceService`, `GoalFactService`, `ResourceCascadeService`, `PlanDistributionService`, `GanttForecastService`, `MorningDigestService`, `AgentRunLogService`, `SyncExternalIdService`, `UserPurgeService`, `InviteService`** — уже существующие сервисы; при миграции своего кластера тикет решает, поглощать ли их логику в новый `XxxService` фичи или оставлять отдельным сфокусированным сервисом (они уже достаточно «глубокие» по `codebase-design` — вероятно, просто становятся зависимостью нового тонкого Service, а не сливаются).
- **Формат `dto/`-пакета для фич с job/scheduled-логикой** (`ProjectPlanFreezeJob`, `DemoFixtureGenerator`, `LifeSphereSeeder`) — это не контроллеры, в скоуп 0.9 не входят по destination, но могут дать наводку на конвенцию для будущих релизов.

## Out of scope

- **`HealthController` и `WolfApiApplication`** — не содержат бизнес-логики/Repository, миграция не даёт пользы.
- **Frontend (`web/`)** — контракт HTTP не меняется (`*ApiIT` — гарантия), Vue-слой не входит в эту карту.
- **Новые фичи, изменение поведения API** — карта только про внутреннее устройство существующих эндпоинтов, не про новые возможности.
- **MapStruct и другие новые библиотеки маппинга** — решение зафиксировано при чертеже (ручные мапперы), не рассматривается.
- **Полный Hexagonal/Clean по всему проекту (вариант C)** — отклонён при чертеже в пользу гибрида D.
- **Формализация портов для будущих каналов бота (Telegram/Max, релизы 0.7+/0.10)** — расширит `note`/датасинк-подобный паттерн, но не в скоупе 0.9; следующая карта, когда бот-каналы будут реализовываться.

## Decisions so far

- **07 (Daily Rituals & Stats) — resolved.** Семь контроллеров (`Checklist`, `ChecklistReport`, `TodayBacklog`, `Focus`, `FocusReview`, `MorningDigest`, `WaveStats`) переведены на thin MVCS; бизнес-логика находится в сервисах, DTO вынесены в feature-specific `dto/` как records. Проверены `clean compileJava compileTestJava` (`BUILD SUCCESSFUL`) и structural scan (`PASS`, 7 controllers, 28 DTO records). Полные `*ApiIT` отложены до release-gate 0.9. Реализация: `72ac23a`, merge в `develop`: `589fc8b`.

- **05 (Roadmap & Load: Gantt, PlanningCapacity, LoadCurve) — resolved.** Три контроллера мигрированы на thin MVCS; DTO вынесены в feature-specific `dto/` records; `GanttForecastService` оставлен отдельной зависимостью. `clean compileJava compileTestJava` и структурные проверки зелёные. Полный IT gate отложен до завершения релиза.

- **08 (Competency & Life Structure: LifeArea, LifeSphere, Synergy) — resolved.** Три контроллера (`LifeArea`, `LifeSphere`, `Synergy`) переведены на thin MVCS; бизнес-логика (проверки дублей имён, reorder/sortOrder, archive-toggle, валидация «ровно один владелец синергии», изоляция по пользователю) перенесена в сервисы `LifeAreaService`/`LifeSphereService`/`SynergyService`; 11 DTO вынесены в `ru.wolf.api.lifearea.dto` и `ru.wolf.api.lifesphere.dto` как records (`from(...)` на самих record'ах). `clean compileJava compileTestJava` — BUILD SUCCESSFUL; structural scan: целевые контроллеры без поля `Repository`, сервисы без веб-аннотаций, все DTO — records. Соседние `*ApiIT`, ссылавшиеся на вложенные `LifeAreaController.*`, поправлены механически (FQN + `getX()`→`x()` + конструкторы records) без изменения поведения. Полные `*ApiIT` отложены до release-gate 0.9.

- **10 (Agent Jobs: AgentController, AgentRunLog) — resolved.** `AgentController` очищен от `UserRepository` и бизнес-логики, делегирует orchestration в новый тонкий `AgentService` (резолв `User` по `authentication.getName()`, вызов `agentJob.runForUser(user)`, обёртка результата в DTO). `AgentRunLogService` **не поглощён** — оставлен отдельным сфокусированным сервисом-зависимостью (правило карты для существующих сервисов). Публичный `RunResponse` вынесен из контроллера в `ru.wolf/api/agent/dto/AgentRunResponse.java` как record с `from(AgentJob.AgentRunResult)`; `AgentApiIT` поправлен механически (FQN + record-accessor) без изменения поведения. `clean compileJava compileTestJava` — BUILD SUCCESSFUL (JDK 21, 36s, локально на хосте — docker-daemon был перегружен). Structural scan: `AgentController` без поля `Repository`, `AgentService` без веб-аннотаций/импортов/ResponseEntity, DTO — record; сквозной grep `private final .*Repository` по всем `*Controller.java` — **0 совпадений** (финальное состояние карты 0.9). Полный `AgentApiIT` отложен до release-gate 0.9.

- **11 (Сквозной аудит + фиксация архитектуры) — resolved.** Карта 0.9 закрыта: все 39 контроллеров (`health`/`WolfApiApplication` — вне контура) не инжектят `Repository` напрямую (сквозной `grep -rl "private final.*Repository" --include='*Controller.java'` — **0 совпадений**); бизнес-логика и `@Transactional` — в сервисах; публичные Request/Response оформлены как records в feature-specific `dto/` (26 пакетов `dto/`, 130+ DTO-records), сервисы без веб-аннотаций. В ходе аудита выявлена аномалия: кластер `delo` (тикет 06, `resolved`) оставил DTO как вложенные `@Data`-классы в `DeloService`/`DeloImportService` + deprecated-наследников в контроллерах, без папки `delo/dto` — нарушение DoD ADR 0005 п.3. Заведён баг `bugs/01-delo-dto-not-in-dto-package.md`; аномалия устранена в рамках этого тикета: 9 DTO (`CreateDeloRequest`, `UpdateDeloRequest`, `DeloResponse`, `DeloDetailResponse`, `ProjectLink`, `RecurrenceSlotDto`, `ApplyRecurrenceRequest`, `ApplyRecurrenceResponse`, `ImportResponse`) перенесены в `ru.wolf.api.delo.dto` как records, в `DeloDetailResponse` добавлен статический `from(delo, factAggregateService, recurrenceService)`, контроллеры очищены от deprecated-наследников, затронутые `*ApiIT` поправлены механически (FQN + canonical-конструкторы + record-accessor) без изменения поведения. `clean compileJava compileTestJava` — BUILD SUCCESSFUL; `DeloApiIT` прогнан изолированно против Testcontainers-postgres — зелёный (чёрный ящик HTTP не изменился). Итоговая архитектура зафиксирована в `AGENTS.md` (раздел «Текущая архитектура API (release 0.9)») как «текущее состояние», а не только как ADR-решение. Полный сквозной прогон всех `*ApiIT` выполняется единым release-gate после слияния 11 в `develop`.

- **09 (Notes & Assistant: Note, NotesAssistant, ProjectResume) — resolved.** `NoteController` → тонкий MVCS (бизнес-логика + `@Transactional` в новом `NoteService`, DTO — records в `ru.wolf.api.note.dto`). `note/assistant` формализован под конвенцию портов: интерфейс `NotesAssistant` → `AssistantPort`, адаптеры переименованы в `FakeNotesAssistantAdapter` (`@Profile("test")`) и `HttpNotesAssistantAdapter` (`@Profile("!test")`) — переключатель fake/http через Spring-profiles сохранён; `LlmDisabledException` вынесен в собственный класс пакета `note.assistant` (маппинг в `GlobalExceptionHandler` → 503 без изменения тела); `ProjectResumeController`/`NotesAssistantController` стали тонкими, делегируют в `NotesAssistantService`; `ResumeResponse` → record в `ru.wolf.api.note.assistant.dto`. `AgentJob` обновлён под переименованный порт. `clean compileJava compileTestJava` — BUILD SUCCESSFUL; structural scan: контроллеры без `Repository`, сервисы без веб-аннотаций, DTO — records. `NoteApiIT` прогнан изолированно против Testcontainers-postgres — 3/3 зелёный; добавлены DB-free unit-тесты (AssistantControllerUnitTest 3/3, GlobalExceptionHandlerTest 1/1) на затронутую логику. `LlmDisabledApiIT`/`NotesAssistantApiIT` не проходят из-за предсуществующего бага H2-драйвера против Testcontainers-postgres (падают на загрузке контекста) — вне скоупа тикета, перепроверяются единым release-gate. Полный кластерный IT gate отложен до завершения релиза.

## Текущее состояние дорожной карты

**Карта 0.9 завершена: тикеты 01–11 — `resolved`.** Сквозной аудит (тикет 11) закрыл
destination карты: ни один контроллер не инжектит `Repository` напрямую, DTO —
records в feature-specific `dto/`, сервисы несут бизнес-логику и `@Transactional`.
Финальная точка перед release-gate (полный сквозной прогон `*ApiIT`).

Реальное состояние (по `develop` на момент тикета 11): тикеты 01–10 слиты в `develop`;
тикет 11 выполнен в ветке `release-0.9/feature/11-final-audit` и вливается в `develop`
перед release-gate.

Последняя подтверждённая точка:

- `01` — **resolved**: ADR 0005 + референсная миграция `Idea`.
- `02` — **resolved**: Identity & Access.
- `03` — **resolved**: Planning & Priorities.
- `04` — **resolved**: Time Grid.
- `05` — **resolved**: Roadmap & Load.
- `06` — **resolved**: Import/Export (порты).
- `07` — **resolved**: Daily Rituals & Stats.
- `08` — **resolved**: Competency & Life Structure.
- `09` — **resolved**: Notes & Assistant (формализация порта).
- `10` — **resolved**: Agent Jobs (AgentController → AgentService, RunResponse → record в agent/dto).
- `11` — **resolved**: Сквозной аудит + фиксация архитектуры (устранён баг 01: delo-DTO → records в `delo/dto`).

Коммиты 01–09 находятся в `develop` (ahead of `origin/develop`). Пользовательские
незакоммиченные изменения в checkout сохранены в stash и не входят в дорожную карту релиза.

## Тикеты

Живут в `.scratch/wayfinder-release-0.9-layered-arch/issues/`. Фронтир — открытые, разблокированные, незанятые.

01. ✅ **resolved** — Паттерн, ADR и референсная миграция (Idea) — блокирует все нижеследующие
02. ✅ **resolved** — Identity & Access: Auth, User, Admin, Invite, Onboarding
03. ✅ **resolved** — Planning & Priorities: Goal, Project, ProjectDependency, Backlog, WeekBacklog
04. ✅ **resolved** — Time Grid: TimeEntry, Calendar, Routine
05. ✅ **resolved** — Roadmap & Load: Gantt, PlanningCapacity, LoadCurve
06. ✅ **resolved** — Import/Export (порты): DataSync, ImportXlsx, Delo, DeloImport
07. ✅ **resolved** — Daily Rituals & Stats: Checklist, ChecklistReport, Today*, Focus, FocusReview, MorningDigest, WaveStats
08. ✅ **resolved** — Competency & Life Structure: LifeArea, LifeSphere, Synergy
09. ✅ **resolved** — Notes & Assistant (формализация существующего порта): Note, NotesAssistant, ProjectResume
10. ✅ **resolved** — Agent Jobs: Agent, AgentRunLog
11. ✅ **resolved** — Сквозной аудит и фиксация архитектуры (устранён баг 01: delo-DTO → records в `delo/dto`)
