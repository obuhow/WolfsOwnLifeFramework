# Баг Б-1 — при создании проекта нет поля финального срока (endDate)

Type: bug
Status: resolved
Найдено при: релиз 1.0 (живой сайт, экран создания проекта)
Регрессия в: `release-0.2` (модель `Project` + `CreateProjectRequest`), Status: resolved

## Доказательство расхождения

Модель и контракт **уже содержат** поле финального срока:

- `api/src/main/java/ru/wolf/api/project/Project.java:70-71` — колонка `end_date`
  (`private LocalDate endDate;`).
- `api/src/main/java/ru/wolf/api/project/dto/CreateProjectRequest.java:37` — поле
  `LocalDate endDate` в record; есть даже конструктор
  `(lifeAreaId, title, startDate, endDate, totalPlanHours)`.
- `web/src/components/ProjectsView.vue:40-41` — `form.endDate` инициализирован `''`;
  `:199-200` — при редактировании читается `project.endDate`;
  `:219-220` — `payloadFromForm()` кладёт `endDate: form.value.endDate || null`;
  `:256` — значение уходит на `POST /projects`.

**Но в шаблоне нет `<input>` для `endDate`.** Рядом с полем «Начало»
(`ProjectsView.vue:370-371`, `<input id="project-start" type="date" v-model="form.startDate">`)
существует только оно. Поля «Конец» / «Финальный срок» нет. Следовательно, пользователь
физически не может задать `endDate` через UI — поле всегда уходит `null`, несмотря на то,
что бэкенд его примет и сохранит.

```text
grep -n "endDate" web/src/components/ProjectsView.vue
  end=40    endDate: '',
  end=185   endDate: '',
  end=200   endDate: project.endDate || '',
  end=220   endDate: form.value.endDate || null,
  end=222   planDistribution: form.value.planDistribution
# Нет ни одной строки вида: <input ... v-model="form.endDate" type="date"
```

## Почему это важно

- Блокирует Б-2: переключатель распределения времени заблокирован, пока нет
  `startDate && endDate && totalPlanHours` (`ProjectsView.vue:389-391`). Без поля
  `endDate` он заблокирован навсегда.
- Проект в глоссарии «опционально имеет сроки» — срок задать нельзя, контракт модели
  не достижим с фронта. Гантт/Дорожная карта лишаются даты финиша проекта.
- Демо-профили (`assets/profiles/*.json`) задают `dueOffsetDays` — то есть срок важен
  для предзаполненных данных, но вручную его повторить нельзя.

## Что сделать

1. Добавить в `ProjectsView.vue` рядом с полем «Начало» (после строки ~371) поле
   «Финальный срок»:
   `<input id="project-end" v-model="form.endDate" type="date" class="input" :disabled="loading" />`.
2. Проверить связку с `planDistribution`: радио разблокируются при
   `startDate && endDate && totalPlanHours` (уже так, изменений в логике не нужно —
   достаточно дать ввести endDate).
3. Браузерная приёмка: создать проект с датой окончания → перезагрузить
   `/projects` → поле «Конец» показывает заданную дату; `GET /projects/{id}`
   содержит `endDate`.
4. Решить, оставлять ли `resolved` у релиза 0.2 — модель закрыта, но вертикальный
   срез (форма) не был; статус меняет владелец.

## Как закрыт

Закрыт в тикете `issues/02-project-deadline-field.md` (добавлен `<input id="project-end">`).
