# Тикет 03 — Переключатель распределения времени (каскад Б-1)

Status: resolved
Blocked by: 02
Type: task

## Вопрос

После появления поля `endDate` (тикет 02) переключатель «Распределение времени»
должен разблокироваться и сохранять выбор (закрывает Б-2,
`bugs/02-distribution-toggle-disabled.md`).

## Что сделать

1. Сама логика блокировки уже корректна: радио `:disabled="!form.startDate || !form.endDate || !form.totalPlanHours"`
   (`ProjectsView.vue:389-391`). После тикета 02 `form.endDate` становится вводимым,
   и при заполнении всех трёх полей радио разблокируются автоматически — **правок
   в этой логике не требуется**, только верификация.
2. Проверить сохранение: выбранный `planDistribution` уходит на
   `POST /projects/{id}/plan-distribution` (`ProjectsView.vue:256-266`, уже реализовано)
   при `form.planDistribution !== 'NONE'`.
3. Убедиться, что `PlanDistributionService.apply` заполняет Записи времени
   (EVEN_ALL_DAYS / EVEN_WEEKDAYS) — бэкенд готов (`api/.../project/PlanDistributionService.java:85-88`).

## Testing Decisions

- Ручная приёмка: ввести `startDate`, `endDate`, `totalPlanHours` → радио
  «Равномерно по дням» / «Равномерно по будням» активны (не `disabled`).
- Выбрать «Равномерно по будням» → Сохранить → в Network один `POST /projects/{id}/plan-distribution`
  с `{"mode":"EVEN_WEEKDAYS"}`; на Гантте (`/roadmap`) проект показывает размазанные часы.
- Подпись «Нужны даты и плановые часы» исчезает, когда все три поля заполнены.
- `PlanDistributionApiIT` (если есть): профиль с `endDate` → `applyPlanDistribution`
  создаёт Записи времени на буднях в интервале.

## Out of Scope

- Новые режимы распределения — только два существующих (EVEN_ALL_DAYS / EVEN_WEEKDAYS).

## Закрывает

Баг Б-2 (`bugs/02-distribution-toggle-disabled.md`). Перевести `Status: open` → `resolved`.

## Answer

Каскадное следствие тикета 02 — закрыто в том же коммите
(`release-1.0/feature/02-03-project-deadline-distribution`).

- Логика блокировки радио не менялась: `:disabled="!form.startDate || !form.endDate
  || !form.totalPlanHours"` (`ProjectsView.vue`). С появлением вводимого поля
  `endDate` (тикет 02) при заполнении всех трёх полей радио «Равномерно по дням» /
  «Равномерно по будням» разблокируются, подпись-заглушка «Нужны даты и плановые
  часы» скрывается через тот же `v-if`.
- Сохранение уже реализовано: при `planDistribution !== 'NONE'` уходит
  `POST /projects/{id}/plan-distribution` с `{ mode }` (`ProjectsView.vue:256-266`).
- Бэкенд готов: `ProjectController` endpoint `plan-distribution` (:87-92),
  `PlanDistributionService.apply` заполняет Записи времени. Покрыто
  `PlanDistributionApiIT` (endpoint с `endDate` + распределением по будням).
- Правок в логике не потребовалось — только разблокировка через наличие поля
  endDate (Out of Scope: новых режимов не вводилось).

Б-2 закрыт.
