# 05 — Проекты (дерево) + страница Проекта

**What to build:** Пользователь создаёт Проекты внутри Области жизни, вкладывает подпроекты, задаёт описание, опциональные сроки и плановые часы всего. Открывает страницу Проекта: поля + список связанных Дел (attach пока можно заглушить до 06, но место есть) + место под агрегаты (агрегаты — в 13).

**Blocked by:** 04 — Области жизни CRUD + RU shell/nav

**Status:** ready-for-human

- [x] CRUD Проект с parent (дерево) и привязкой к одной Области
- [x] Поля: title, description, start/end, total plan hours
- [x] Страница Проекта в SPA
- [x] API-тесты дерева и ownership

## Implementation notes

### Backend
- Entity `Project` (`project`): `user_id`, `life_area_id`, optional `parent_id`, `title`, `description`, `start_date`, `end_date`, `total_plan_hours`
- REST `/api/v1/projects`:
  - `GET` — flat list (optional `?lifeAreaId=`), ordered by title; tree assembled on client
  - `GET /{id}` — detail: area/parent names + empty `delos[]` + `aggregates: null` (06/13)
  - `POST` — create root or nested; parent must be same life area + same owner
  - `PUT /{id}` — update fields/parent/area; cycle detection; moving area reassigns subtree
  - `DELETE /{id}` — deletes subtree depth-first (JPA self-ref; not only DB CASCADE)
- Ownership via `findByUserAndId` + JOIN FETCH lifeArea/parent
- Flyway `V3__create_project_table.sql`
- Validation: end ≥ start, unknown area/parent → 400 RU message

### Frontend
- Nav: **Проекты**
- `ProjectsView`: tree by parent, filter by area, create/edit/delete, nested create
- `ProjectDetailView` `/projects/:id`: card fields, children, placeholders for Дела + агрегаты
- Hash routes wired in `main.js`

### Verification
- `ProjectApiIT` green (13 scenarios: CRUD, nest, filter, detail stubs, cycle, dates, cascade delete, isolation, unauth 403)
- Compose smoke (2026-08-10):
  - Flyway → v3
  - health UP, login admin/admin
  - create area → root/child project → list/filter/detail/update
  - cycle parent → 400
  - delete cascade → empty list
  - unauth → 403
  - web :5174 → 200
- Branch: `feature/05-projects-tree`
