# 13 — Агрегаты на карточках Проекта и Дела

**What to build:** На страницах Проекта и Дела Пользователь видит совокупно, в какие дни и часы велись работы (на основе выполненных/учтённых Записей времени и правил атрибуции).

**Blocked by:** 07 — Записи времени; 12 — Гантт (переиспользовать правила факта)

**Status:** ready-for-human

- [x] Aggregate endpoint/section for Проект
- [x] Aggregate endpoint/section for Дело
- [x] UI blocks on both settings pages
- [x] API-тесты согласованности с gantt fact rules

## Implementation notes

### Backend
- `FactAggregate` DTO: `totalFactHours`, `hourAccountingMode`, `byDay[{date, hours}]`
- `FactAggregateService` — shared fact rules with Gantt:
  - DONE only; PLANNED excluded
  - ad-hoc (no Дело) excluded from project fact
  - `PRIMARY_ONLY` → only primary project of the Дело
  - `ALL_PROJECTS` → full hours on each linked project
  - day buckets via user `dayEnd` (`DayBounds`)
- Wired into:
  - `GET /api/v1/projects/{id}` → `aggregates`
  - `GET /api/v1/delos/{id}` → `aggregates`
- Delo aggregate = sum of DONE entries for that Дело (no project split)

### Frontend
- `ProjectDetailView.vue` — блок «Агрегаты времени» (total + by-day table)
- `DeloDetailView.vue` — блок «Фактические часы»
- Shared styles in `style.css` (`.aggregate-table`, `.aggregate-total`)

### Verification
- `AggregateApiIT` green (primary_only, all_projects, ad-hoc, dayEnd, delo, gantt week sum match)
- `ProjectApiIT` placeholder assertion updated to empty aggregate
- `npm run build` OK
- Branch: `feature/13-project-delo-aggregates`
