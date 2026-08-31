# Тикет 02 — «План нагрузки» выносится в отдельную страницу /load-plan

Status: ready-for-agent
Blocked by:
Type: task

## Вопрос

Заявка (Дорожная карта): «План нагрузки отображается на отдельной странице». Сейчас это
**секция-якорь** внутри Дорожной карты (`/roadmap#load-plan`), а не отдельный маршрут.
Решение владельца (grill-with-docs реш. B): вынести в отдельную страницу `/load-plan`;
старый якорь `/roadmap#load-plan` — редирект 302, как ранее `/gantt`,`/planning`→`/roadmap`.

## Доказательство текущего состояния

- `web/src/components/LoadCharts.vue:160` — `<section id="load-plan" ... aria-label="План нагрузки">`,
  живёт внутри `PlanningView.vue` (`PlanningView.vue:21`: «Секция „План нагрузки" (#load-plan)
  целиком вынесена в LoadCharts.vue, release 0.8»).
- `web/src/main.js:84` — `{ path: '/roadmap', component: PlanningView, meta: { requiresAuth: true } }`;
  редиректы-образцы: `main.js:82-83` (`/gantt`,`/planning` → `/roadmap`).
- Пункт меню `App.vue:53` — `{ label: 'План нагрузки', to: '/roadmap#load-plan' }`.
- Внутри `LoadCharts.setTab` (`LoadCharts.vue:64-71`) вид пишется в query на `/roadmap` —
  при выносе базовый путь меняется на `/load-plan`.

## Что сделать

Фронт (роутинг + компоновка):

1. `main.js`: добавить маршрут `{ path: '/load-plan', component: <LoadPlanView>, meta: { requiresAuth: true } }`.
   Вынести секцию `LoadCharts` из `PlanningView` в самостоятельный экран страницы
   (`LoadPlanView.vue` — тонкая обёртка-страница с заголовком «План нагрузки» + `LoadCharts`).
2. `PlanningView.vue` (`/roadmap`): убрать встроенную секцию «План нагрузки»; вместо неё —
   при необходимости ссылка «Открыть План нагрузки» на `/load-plan` (без дублирования диаграмм).
3. Совместимость якоря: `/roadmap#load-plan` → **302** на `/load-plan` (или навигационный
   guard, редиректящий hash-переход). Сохранить deep-link на вкладку: `/load-plan?chart=<key>`
   (в `LoadCharts.setTab` заменить базовый путь `'/roadmap'` на `'/load-plan'`).
4. Меню `App.vue:53`: `{ label: 'План нагрузки', to: '/load-plan' }`.
5. Пункт «Дорожная карта» (`App.vue:52`, `/roadmap`) остаётся — теперь только Гантт.

## Testing Decisions

- Браузер: переход по меню «План нагрузки» открывает `/load-plan` со всеми вкладками;
  `/roadmap#load-plan` редиректит на `/load-plan`; `/load-plan?chart=ladder` открывает
  вкладку «Лестница» без перезагрузки данных (deep-link сохранён).
- `/roadmap` показывает Гантт без встроенной секции «План нагрузки» и без её дубля.
- `npm run build` зелёный; served-бандл содержит новый маршрут.

## Out of Scope

- Порядок вкладок — тикет 03. Логика прогнозов вкладок (`loadChartsForecast.js`) — не трогаем.
- Кнопка «Распределить время» и редактор кривой — тикет 04.

## Закрывает

Доработка «Дорожная карта: План нагрузки на отдельной странице» (реш. B). Блокирует 03, 04.

## Комментарий по глоссарию

`CONTEXT.md` «План нагрузки» уже переведён из «(секция)» в «(страница)» (`/load-plan`,
302 со старого якоря) в docs-проходе grill-with-docs.
