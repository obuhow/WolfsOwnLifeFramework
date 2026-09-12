# 12 — Гантт: plan/fact, фильтры, режим учёта

**What to build:** Пользователь видит Гантт: дерево Проектов, недели с подписями месяцев, подсветка текущей недели, default старт = понедельник прошлой недели. Редактирует План на неделю на пересечении; видит факт-часы из Записей времени. Фильтры по Областям и «только с сроками». Режим учёта часов (settings) primary_only vs all_projects; ad-hoc не в факте проектов. Полоски, full width.

**Blocked by:** 03 — Настройки; 05 — Проекты; 07 — Записи времени

**Status:** ready-for-human

- [x] API week-plan upsert + gantt aggregate query
- [x] Fact hours respect hour accounting mode + primary
- [x] Ad-hoc excluded from project fact
- [x] UI Gantt filters, month headers, current week, strips
- [x] API-тесты обоих режимов учёта и plan write

## Implementation notes

### Backend
- Flyway `V8__week_plan.sql` — `week_plan(user, project, iso_year, iso_week, plan_hours)` unique
- Entity `WeekPlan` + `WeekPlanRepository`
- REST `/api/v1/gantt`:
  - `GET` — дерево проектов × недели; `planHours` / `factHours` per cell
    - default `from` = Monday of previous week; `weeks` default 16 (max 52)
    - filters: `lifeAreaIds` (csv), `onlyWithDates`
    - week columns: month labels (RU), `current` flag
    - fact = DONE time entries only; ad-hoc excluded
    - `PRIMARY_ONLY` → primary project; `ALL_PROJECTS` → full hours on each linked project
    - week bounds use user `dayEnd` via `DayBounds`
  - `PUT /week-plans` — upsert plan; `0`/null clears cell
- Ownership via `findByUserAndId`; unauth → 403

### Frontend
- Nav: **Гантт** → `/gantt`
- `GanttView.vue`: sticky project col, month headers, current week highlight, plan/fact strips
- Inline plan edit (click / dblclick), area checkboxes, «только с сроками», week nav
- Full-bleed via `.app-main:has(.gantt-page)`

### Verification
- `GanttApiIT` green (plan upsert/clear, primary_only, all_projects, ad-hoc excluded, planned≠fact, filters, depth, isolation, unauth)
- `npm run build` OK
- Branch: `feature/12-gantt-plan-fact`
