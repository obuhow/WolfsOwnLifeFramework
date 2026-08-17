# WOLF Release 0.1

Status: `ready-for-agent`  
Feature slug: `release-0.1`  
Tracker: local markdown (this file)  
Glossary: `CONTEXT.md`  
ADRs: `docs/adr/0001-personal-single-user-data.md`, `0002-stack-spring-vue-postgres.md`, `0003-release-0-1-scope.md`

---

## Problem Statement

Люди 25–40 (предприниматели, фрилансеры, художники, разработчики) живут в хаосе задач и календарей. Им не нужен ещё один строгий тайм-менеджер с красными уведомлениями. Нужно одно **уютное, тихое, под их контролем** место, куда можно перенести жизнь как есть: 15-минутный ритм дня, свои Области жизни, Проекты, Дела, план и факт времени, без культа продуктивности и без обязательных AI-агентов в первой версии.

Сейчас такого согласованного продукта в репозитории нет — только договорённости в глоссарии и ADR. Нужна первая рабочая версия (0.1), в которой можно жить в сетке «Сегодня/Неделя», видеть Гантт проектов, настраивать Дела и Проекты, один раз импортировать CSV/ICS и войти как Пользователь (seed `admin`).

## Solution

Собрать **WOLF 0.1** — multi-user-ready personal web-приложение (данные строго изолированы по Пользователю):

- JWT-вход, seed-пользователь `admin`
- Области жизни → Проекты (дерево) → Дела (0..N проектов, с Основным проектом)
- Записи времени в 15-минутных ячейках со статусами неопределено / запланирована / выполнена
- Экраны: Сегодня, Неделя, Гантт, карточки Проекта и Дела
- Бэклог недели, ночные часы + авто-Дело «Сон», простое Правило повторения
- План на неделю и факт-часы на Гантте с Режимом учёта часов
- One-shot импорт CSV (Дела) **(ICS отменён — перенесён в release-cancelled)**
- Стек: Java 21 + Spring Boot 3 + Gradle, Vue 3, PostgreSQL, JWT, Docker Compose
- UI на русском; неделя пн–вс; timezone Пользователя (default `Europe/Moscow`)

Агенты, live OAuth-календари, RRULE, шеринг — вне 0.1 (ADR-0003).

## User Stories

1. As a Пользователь, I want to log in with my account, so that my life data stays private to me.
2. As a developer, I want a seed user `admin`, so that I can open the app immediately after bootstrap.
3. As a Пользователь, I want a second account’s data never to appear in my views, so that I trust isolation.
4. As a Пользователь, I want to set my timezone, so that «сегодня» and week boundaries match my real day.
5. As a Пользователь, I want the UI in Russian, so that the product feels native.
6. As a Пользователь, I want weeks to run Monday–Sunday, so that planning matches my habit.
7. As a Пользователь, I want to create and rename Области жизни, so that work/health/family/etc. fit my frame.
8. As a Пользователь, I want to create Проекты inside an Область жизни, so that initiatives have a home.
9. As a Пользователь, I want nested Проекты (подпроекты), so that large efforts stay structured.
10. As a Пользователь, I want optional start/end dates on a Проект, so that the Gantt can show timeboxes.
11. As a Пользователь, I want optional total plan hours on a Проект, so that I see the intended effort size.
12. As a Пользователь, I want to create Дела with title and description, so that reusable activities exist outside the grid.
13. As a Пользователь, I want to attach a Дело to zero or more Проекты, so that routines need not force a project.
14. As a Пользователь, I want to mark one Основной проект when a Дело has projects, so that primary hour accounting works.
15. As a Пользователь, I want a Способ исполнения on each Дело (мне / делегировать / автоматизировать), so that Today lists can filter intent without multi-user assignment.
16. As a Пользователь, I want to open Сегодня and see a 15-minute grid from 00:00 to 23:45, so that my day is tangible.
17. As a Пользователь, I want night hour rows hidden by default per my settings, so that the day view stays calm.
18. As a Пользователь, I want to show night hours on demand, so that I can edit sleep or late work.
19. As a Пользователь, I want to configure Ночные часы, so that sleep autofill matches my rhythm.
20. As a Пользователь, I want empty night cells to autofill Дело «Сон», so that rest is captured without busywork.
21. As a Пользователь, I want a manual entry in a night cell to override sleep, so that I stay in control.
22. As a Пользователь, I want to click a future empty cell and place a Запись времени as запланирована, so that I can plan ahead.
23. As a Пользователь, I want to click a planned future cell again to clear it, so that plans stay flexible.
24. As a Пользователь, I want to click a past empty cell and place a Запись времени as выполнена, so that I can log facts quickly.
25. As a Пользователь, I want past planned entries to stay запланирована until confirmed, so that the system does not lie that I did them.
26. As a Пользователь, I want «Подтвердить все» for the visible range, so that I can accept a block of past plan as fact.
27. As a Пользователь, I want to confirm a single past planned cell manually, so that I can fix exceptions.
28. As a Пользователь, I want at most one Запись времени per 15-minute cell, so that the grid stays simple.
29. As a Пользователь, I want to pick a Дело from project-structured lists when filling a cell, so that placement is fast.
30. As a Пользователь, I want to type ad-hoc text into a cell without creating a Дело, so that one-offs do not pollute the catalog.
31. As a Пользователь, I want a right-side «на сегодня» list, so that I see what is already on today’s grid.
32. As a Пользователь, I want a Бэклог недели basket, so that weekly intent is separate from calendar placement.
33. As a Пользователь, I want to add and remove Дела from the current week’s backlog, so that the basket stays true.
34. As a Пользователь, I want filters «мне» / «делегировать» / «автоматизировать», so that I can focus the Today panel.
35. As a Пользователь, I want a Неделя view with seven 15-minute columns, so that I can plan the whole week.
36. As a Пользователь, I want a week picker, so that I can move between weeks.
37. As a Пользователь, I want to create a quick Дело from the week UI, so that capture is not blocked by navigation.
38. As a Пользователь, I want a Gantt of Проекты with nested rows, so that portfolio time is visible.
39. As a Пользователь, I want the default Gantt range to start at Monday of the previous week, so that recent context is visible.
40. As a Пользователь, I want month names above week columns, so that I can orient in the year.
41. As a Пользователь, I want the current week column highlighted, so that “now” is obvious.
42. As a Пользователь, I want checkbox filters by Область жизни, so that the Gantt is not noisy.
43. As a Пользователь, I want «only projects with dates», so that undated noise can hide.
44. As a Пользователь, I want fact hours on the Gantt from calendar Записи времени, so that reality shows up.
45. As a Пользователь, I want to set План на неделю on a project×week cell, so that intention is explicit.
46. As a Пользователь, I want plan and fact as compact strips, so that the table does not become endlessly tall.
47. As a Пользователь, I want full viewport width for Gantt (and generally the app), so that weeks remain readable.
48. As a Пользователь, I want a setting Режим учёта часов defaulting to primary-only, so that total life hours are not double-counted by default.
49. As a Пользователь, I want to switch to all-projects hour mode, so that each project can show full attributed time when I want that view.
50. As a Пользователь, I want ad-hoc cells (no Дело) excluded from project fact hours, so that uncategorized time does not distort projects.
51. As a Пользователь, I want a Проект settings page with title, description, dates, links to Дела, and worked aggregates, so that I can manage an initiative in one place.
52. As a Пользователь, I want a Дело settings page with title, description, projects, primary, recurrence action, and worked aggregates, so that templates stay maintainable.
53. As a Пользователь, I want a simple Правило повторения (weekdays + optional window + horizon), so that routines land on the calendar without RRULE complexity.
54. As a Пользователь, I want applying recurrence to create future planned Записи времени only within the horizon, so that the calendar does not fill forever.
55. As a Пользователь, I want changing recurrence not to rewrite past done entries, so that history stays honest.
56. As a Пользователь, I want to upload a CSV of tasks once, so that existing task chaos becomes Дела.
57. As a Пользователь, I want an option to drop imported Дела into the current Бэклог недели, so that import feeds this week’s intent.
58. As a Пользователь, I want to upload an ICS once, so that existing calendar events become Записи времени. **(cancelled — moved to release-cancelled)**
59. As a Пользователь, I want long ICS events split/rounded onto 15-minute cells, so that they fit the grid model. **(cancelled — moved to release-cancelled)**
60. As a Пользователь, I want the product to feel quiet (no red nagging cult), so that it matches the brand promise.
61. As a developer, I want Docker Compose to run API + DB (+ frontend serve path), so that local setup is repeatable.
62. As an implementing agent, I want acceptance behaviour locked at the HTTP API seam, so that UI and backend can evolve without ambiguous “done”.

## Implementation Decisions

### Architecture & stack
- Greenfield modular monolith: Spring Boot 3 API + Vue 3 SPA + PostgreSQL.
- Build: Gradle (backend). Frontend: Vue 3 toolchain as chosen at scaffold (Vite expected).
- Auth: JWT bearer; password hashing; bootstrap seed Пользователь `admin`.
- Tenancy: every row owned by `userId`; no shared workspace (ADR-0001).
- Deploy/dev: Docker Compose for Postgres + app services (ADR-0002).
- Locale: RU UI strings in 0.1; week start Monday; user IANA timezone default `Europe/Moscow`.

### Primary modules (logical)
- **Identity** — register/login (minimal), JWT, seed admin, current user.
- **Life structure** — Область жизни, Проект (tree), Дело, links, primary project, execution mode.
- **Calendar** — Запись времени, status transitions, confirm-all, night hours settings, sleep autofill, week backlog.
- **Planning views support** — queries for day/week grids; Gantt aggregates; План на неделю writes.
- **Recurrence** — simple rule storage + apply-to-horizon command.
- **Import** — CSV → Дела; **ICS → Записи времени (cancelled, moved to release-cancelled)**; 15-min normalization rules.
- **Web app** — pages: login, Сегодня, Неделя, Гантт, Проект, Дело, import entry points, user settings (night hours, hour mode, timezone).

### Domain rules to encode
- Дело ≠ Запись времени (glossary).
- Status model and click semantics as agreed in grill (future plan/clear; past fact; planned persists until confirm).
- One entry per (user, 15-min start).
- Ad-hoc text allowed without Дело; no auto-create Дело.
- Night autofill only when cell empty; manual overrides sleep.
- Hour accounting: `primary_only` default vs `all_projects`; user setting.
- Ad-hoc excluded from project fact.
- Recurrence: weekdays + optional window + finite horizon; do not rewrite `done` history.

### API shape (contract level, not paths carved in stone)
- Resource-oriented JSON under versioned prefix (e.g. `/api/v1`).
- Auth endpoints + CRUD for areas/projects/delos/time-entries/backlog/week-plans.
- Commands: confirm-all, apply-recurrence, import CSV. **(import ICS cancelled — moved to release-cancelled)**
- All mutations authenticated; server enforces ownership.

### Persistence
- PostgreSQL relational schema mirroring domain nouns above.
- Timestamps stored in UTC; interpret grids via user timezone.
- Unique constraint on time entry (user_id, start_at).

### Frontend
- Vue 3 SPA talks to API with JWT.
- Screens per Solution; prefer full width; calm styling.
- No mandatory component-test matrix in 0.1 beyond what keeps API honest.

### Seams
- **Primary test seam: authenticated HTTP API** against real PostgreSQL (Testcontainers or Compose-backed).
- Vue has no required automated E2E suite in 0.1; manual smoke of grids is enough for release gating unless API already proves behaviour.
- CSV/ICS parsing is not a second product seam unless API tests become unreadable — then extract pure parsers still invoked only via import use cases.

## Testing Decisions

### What good tests are
- Assert **external behaviour** through the primary seam (HTTP + DB), not private methods, Vue internals, or SQL shape trivia.
- Names and data use glossary terms (Дело, Запись времени, Область жизни, …).
- Prefer scenario tests that mirror user stories (login isolation, click-equivalent status transitions via API, gantt aggregates, import).

### What is tested (0.1)
- Identity: login, seed admin, cross-user isolation.
- Life structure CRUD + project tree + delo links/primary/execution mode.
- Time entries: uniqueness, status transitions, confirm-all, night sleep autofill rules.
- Week backlog membership per ISO week.
- Week plan write/read; fact hour aggregation for both accounting modes; ad-hoc excluded.
- Recurrence apply horizon and non-rewriting of done history.
- CSV import observable results via subsequent GETs. **(ICS import cancelled — moved to release-cancelled)**

### Prior art
- None in-repo yet. Establish one testing style: Spring Boot tests + Testcontainers PostgreSQL + authenticated `MockMvc`/`WebTestClient` (or REST-assured) as the default template for later tickets.

### Explicitly not required for 0.1 done
- Full Playwright suite of the 15-minute grid.
- Visual regression.
- Load/performance beyond smoke.

## Out of Scope

- AI agents, chat, auto-sort, auto-schedule, smart reminders
- Google/Outlook (or any) OAuth live calendar sync
- Full RRULE engine, exceptions, reschedule-following
- Shared workspaces, family sharing, guest access, domain “admin role”
- Native mobile apps
- English UI, i18n framework beyond RU strings
- Push/email notification cult
- Splitting hours evenly across projects (only primary_only vs all_projects)
- Multiple Записи времени in one 15-minute cell
- Treating empty cells as rich “unknown activity” beyond empty/undefined
- Billing, orgs, SSO

## Further Notes

- Prior free-form draft lived in this path and is fully replaced by this `to-spec` document.
- Product tone: warm, quiet, under user control — UX copy and defaults should not shame the user.
- Import file schemas (exact CSV columns) may be fixed during implementation and documented beside import code; behaviour must still meet stories 56–57. **(ICS stories 58-59 cancelled — moved to release-cancelled)**
- After this spec, split work with `to-tickets` into `.scratch/release-0.1/issues/NN-*.md` rather than coding from the whole release at once.
- Implementation must keep `CONTEXT.md` vocabulary; if a term shifts, update glossary via domain-modeling — do not silently invent synonyms in code/UI.
