# Баг Б-2 — переключатель распределения времени всегда заблокирован

Type: bug
Status: resolved
Найдено при: релиз 1.0 (живой сайт, экран создания/редактирования проекта)
Регрессия в: `release-0.2` (модель `planDistribution`), Status: resolved

## Доказательство расхождения

В `web/src/components/ProjectsView.vue` переключатель «Распределение времени»:

```html
<fieldset class="distribution-options">
  <legend>Распределение времени</legend>
  <label><input v-model="form.planDistribution" value="EVEN_ALL_DAYS" type="radio"
    :disabled="!form.startDate || !form.endDate || !form.totalPlanHours" />
    Равномерно по дням</label>
  <label><input v-model="form.planDistribution" value="EVEN_WEEKDAYS" type="radio"
    :disabled="!form.startDate || !form.endDate || !form.totalPlanHours" />
    Равномерно по будням</label>
  <small v-if="!form.startDate || !form.endDate || !form.totalPlanHours" class="muted">
    Нужны даты и плановые часы</small>
</fieldset>
```

Радио заблокированы, пока **все три** условия не выполнены: `startDate`, `endDate`,
`totalPlanHours`. Поскольку поле `endDate` отсутствует в форме (см. Б-1,
`bugs/01-project-no-deadline-field.md`), `form.endDate` всегда `''`, и радио
**никогда не разблокируются** — видна только подпись «Нужны даты и плановые часы».

Это каскадный баг: пользователь видит подпись-заглушку вместо рабочего переключателя,
потому что одно из требуемых полей (endDate) невозможно ввести.

## Почему это важно

- «Равномерно по дням / по будням» — это заполнение плановых Записей времени проекта
  через `POST /projects/{id}/plan-distribution` (`ProjectController.java:87-93` →
  `PlanDistributionService.apply`). Без разблокировки радио эта возможность
  недостижима с фронта, хотя бэкенд и сервис готовы.
- Глоссарий: «Проект … плановые часы» + enum `PlanDistribution` — контракт не достижим.

## Что сделать

1. Закрыть Б-1 (добавить поле `endDate`) — это уберёт постоянную блокировку.
2. После Б-1 проверить браузерно: ввести `startDate`, `endDate`, `totalPlanHours` →
   радио разблокируются; выбрать «Равномерно по будням» → сохранить →
   `POST /projects/{id}/plan-distribution` уходит с `mode: EVEN_WEEKDAYS` (логика уже
   в `ProjectsView.vue:256-266`); на Гантте появляются размазанные часы.
3. Решить, оставлять ли `resolved` у релиза 0.2.

## Как закрыт

Закрыт в тикете `issues/03-distribution-toggle.md` (как каскадное следствие Б-1 + проверка
разблокировки и сохранения распределения).
