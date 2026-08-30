# Тикет 05 — Вложенность проектов в Утреннем обходе

Status: ready-for-agent
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
