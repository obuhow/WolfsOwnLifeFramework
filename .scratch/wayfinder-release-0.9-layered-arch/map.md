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
  - **Definition of done на тикет**: 1) контроллер фичи не содержит поле `Repository`; 2) вся бизнес-логика — в `Service` (или порт/адаптер, если применимо); 3) публичные Request/Response — records в `dto/`; 4) все существующие `*ApiIT` этой фичи проходят без правок их кода; 5) `GlobalExceptionHandler` и доменные исключения (`DuplicateGoalPriorityException` и т.п.) не переносятся без необходимости — остаются рядом с моделью, как сейчас.
  - **Исключение**: `HealthController` (нет обращения к БД/бизнес-логики) и `WolfApiApplication` не мигрируются — вне контура рефакторинга.
- **Порядок**: тикет 01 (паттерн + ADR + референсная миграция) **блокирует все фиче-кластеры** — так все параллельные сессии/агенты идут по одному проверенному образцу, а не изобретают вариации. Финальный тикет — сквозной аудит — блокируется всеми кластерами.
- **Границы фиче-кластеров ниже — ориентир, не жёсткий реестр.** Список контроллеров по каждому кластеру взят на момент чертежа; перед началом работы по тикету агент сверяет актуальный список `find src/main/java -name '*Controller.java'` для своей фичи — код мог измениться.

## Decisions so far

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

## Тикеты

Живут в `.scratch/wayfinder-release-0.9-layered-arch/issues/`. Фронтир — открытые, разблокированные, незанятые.

01. Паттерн, ADR и референсная миграция (Idea) — блокирует все нижеследующие
02. Identity & Access: Auth, User, Admin, Invite, Onboarding
03. Planning & Priorities: Goal, Project, ProjectDependency, Backlog, WeekBacklog
04. Time Grid: TimeEntry, Calendar, Routine
05. Roadmap & Load: Gantt, PlanningCapacity, LoadCurve
06. Import/Export (порты): DataSync, ImportXlsx, Delo, DeloImport
07. Daily Rituals & Stats: Checklist, ChecklistReport, Today*, Focus, FocusReview, MorningDigest, WaveStats
08. Competency & Life Structure: LifeArea, LifeSphere, Synergy
09. Notes & Assistant (формализация существующего порта): Note, NotesAssistant, ProjectResume
10. Agent Jobs: Agent, AgentRunLog
11. Сквозной аудит и обновление CONTEXT.md/AGENTS.md — блокируется тикетами 02–10
