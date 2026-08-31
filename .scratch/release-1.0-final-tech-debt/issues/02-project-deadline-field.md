# Тикет 02 — Поле финального срока проекта в форме

Status: resolved
Blocked by:
Type: task

## Вопрос

Добавить в экран создания/редактирования проекта поле «Финальный срок» (endDate),
которое сейчас есть в модели и DTO, но отсутствует в шаблоне (закрывает Б-1,
`bugs/01-project-no-deadline-field.md`). Это также разблокирует Б-2.

## Что сделать

1. `web/src/components/ProjectsView.vue` — после поля «Начало»
   (`<input id="project-start" ... v-model="form.startDate" type="date">`, ~строка 371)
   добавить поле «Финальный срок»:
   ```html
   <div class="form-group">
     <label for="project-end">Финальный срок</label>
     <input id="project-end" v-model="form.endDate" type="date" class="input" :disabled="loading" />
   </div>
   ```
2. `form.endDate` уже инициализирован `''` (строки 41/185), читается из проекта
   (строка 200) и уходит в payload (строка 220) — **правок в скрипте не требуется**.
3. Отображение в списке проектов уже есть (`ProjectsView.vue:444-446`,
   `{{ row.startDate || '…' }} — {{ row.endDate || '…' }`) — убедиться, что при
   пустом `endDate` выводится корректная заглушка (уже `|| '…'`).

## Testing Decisions

- Ручная приёмка: создать проект с датой окончания → перезагрузить `/projects` →
  колонка срока показывает заданную дату.
- `ProjectApiIT` (существующий): `POST /projects` с `endDate` → `GET /projects/{id}`
  возвращает то же `endDate` (контракт уже поддержан бэкендом, проверка без правок бэкенда).
- Браузерная инспекция DOM: `<input id="project-end">` присутствует и не `disabled`
  без причины.

## Out of Scope

- Миграция БД — колонка `end_date` уже существует (`Project.java:70-71`).
- Изменение логики `planDistribution` — только разблокировка через наличие поля.

## Закрывает

Баг Б-1 (`bugs/01-project-no-deadline-field.md`). Перевести `Status: open` → `resolved`.

## Answer

Реализовано в ветке `release-1.0/feature/02-03-project-deadline-distribution`
(от `origin/develop`, уже на 1.0.0).

- `web/src/components/ProjectsView.vue`: после поля «Начало» добавлен блок
  `<div class="form-group"><label for="project-end">Финальный срок</label>
  <input id="project-end" v-model="form.endDate" type="date" class="input"
  :disabled="loading" /></div>`.
- Скрипт не трогался: `form.endDate` уже инициализировался `''`, читался из
  `project.endDate` при редактировании и уходил в payload как `endDate || null`.
- Бэкенд не менялся (Out of Scope): колонка `end_date` (`Project.java:70-71`),
  `CreateProjectRequest.endDate` (:37) и endpoint plan-distribution уже на месте.
  Round-trip endDate покрыт `ProjectApiIT` (assert `created.endDate()` = 2026-09-30).
- Приёмка build-гейт: `npm run build` в `web/` проходит (шаблон компилируется).
  Полная браузерная приёмка (создать проект с датой → перезагрузить `/projects` →
  колонка срока показывает дату) идёт на общем визуальном этапе релиза; поле
  и его связи с state подтверждены в коде.

Б-1 закрыт. Статус исходного релиза 0.2 не трогался (по решению владельца —
модель была закрыта, недоделан был только вертикальный срез формы).
