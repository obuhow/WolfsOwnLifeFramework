# Тикет 05 — Вложенность проектов в Утреннем обходе

Status: resolved
Blocked by:
Type: task

## Вопрос

В интерфейсе «Утренний обход» (`/morning`) родительский и дочерний проекты должны
отображаться иерархически (Проект WOLF → раскрывает WOLF MVP), а не на одном уровне
(Доработка 1 заявки).

## Что сделать

1. **Бэкенд** — `api/.../morning/ProjectDigest.java` добавить поля
   `Long parentId` и `int depth` (как в `gantt/dto/ProjectRow.java:5`, где уже есть
   `parentId` и `depth`). В `MorningDigestService.build` (`:79-82`) строить дерево:
   - запросить все `IN_PROGRESS` проекты пользователя (`findByUserOrderByTitleAsc`);
   - вычислить `depth` обходом `parent` до корня;
   - отсортировать: сначала корни по title, у каждого — дети по title (дерево, не плоский список).
   - `ProjectDigest` получает `parentId` (для сворачивания на фронте) и `depth`.
2. **Фронт** — `web/src/components/MorningView.vue`:
   - `digest.projects` теперь древовидный (или плоский с `depth`/`parentId` — выбрать
     один формат и зафиксировать в `MorningDigestResponse`). Рендерить с отступом
     по `depth` и кнопкой раскрытия у узлов с детьми.
   - Сохранить существующую логику `toggle(projectId)` / `expanded` (она уже есть,
     строки 29-31, 89) — теперь раскрывает именно поддерево, а не весь список.
   - При `depth === 0` — заголовок уровня родителя; `depth > 0` — отступ
     (`margin-left: calc(depth * 1rem)`), визуально «вложенный».

## Testing Decisions

- `MorningDigestApiIT`: пользователь с проектом WOLF (parent=null) и дочерним
  WOLF MVP (parent=WOLF) → `GET /morning-digest` возвращает ровно один корень
  WOLF, у которого `children`/вложенный узел — WOLF MVP; `depth` WOLF = 0, WOLF MVP = 1.
- Браузерная приёмка: Утренний обход → Проект WOLF раскрывается, внутри виден
  WOLF MVP с отступом; свернуть → дочерний скрыт.
- Тихий контракт 0.3: без цветных маркеров иерархии, только отступ и правило.

## Out of Scope

- Фильтрация по вложенности на других экранах (Проекты/Гантт) — там `depth` уже есть
  в `ProjectRow`; тикет только про Утренний обход.

## Закрывает

Доработка 1 заявки.

## Answer

Реализовано в ветке `release-1.0/feature/05-morning-nested` (от `origin/develop`).
Формат зафиксирован: **плоский pre-order список с `depth`/`parentId`** (как в
`gantt/dto/ProjectRow`), без вложенных `children` в JSON.

**Бэкенд:**
- `morning/dto/ProjectDigest` — добавлены `Long parentId` и `int depth` (порядок полей:
  `id, parentId, depth, title, lastNotes, topDelos`).
- `MorningDigestService` — метод `build` теперь зовёт `buildProjectTree(user, week)`:
  берёт `IN_PROGRESS` проекты (`findByUserOrderByTitleAsc` — уже по title), делит на
  корни и `childrenByParent` (LinkedHashMap хранит title-порядок), затем pre-order
  обходом `appendSubtree` строит плоский список: корень по title → его дети по title →
  их дети и т.д. `depth` = число видимых предков, `parentId` = ближайший видимый предок.
- `nearestVisibleParentId` — если родитель не `IN_PROGRESS` (не отображается), ребёнок
  поднимается в корни (не теряется под скрытым родителем). Guard от циклов (100).

**Фронт (`MorningView.vue`):**
- Цикл рендерит `visibleProjects` (computed): строка видна, только если раскрыты ВСЕ её
  предки (`isVisible` идёт вверх по `parentId` через `projectsById`). Раскрытие корня
  через существующий `toggle`/`expanded` показывает поддерево.
- Отступ по глубине `:style="marginLeft: depth*20px"`, класс `morning-project-nested`
  (левый субрул), подсказка «· подпроекты» у узлов с детьми (`hasChildren`).
- Тихий контракт 0.3: только отступ и левая линия, без цветных маркеров.

**Приёмка:**
- `MorningDigestApiIT` — **3/3 PASSED** (BUILD SUCCESSFUL). Новый тест
  `digest_nests_child_project_under_parent_with_depth`: WOLF (root, depth 0, parentId
  null) + WOLF MVP (depth 1, parentId = WOLF), ровно 2 записи в pre-order. Два прежних
  теста зелёные — DTO-изменение обратно совместимо, регрессии нет.
- `npm run build` в `web/` — компонент компилируется.

Доработка 1 закрыта.
