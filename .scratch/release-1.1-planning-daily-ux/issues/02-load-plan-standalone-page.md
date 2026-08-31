# Тикет 02 — «План нагрузки» выносится в отдельную страницу /load-plan

Status: resolved
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

## Answer

Реализовано на ветке `release-1.1/feature/02-load-plan-standalone-page` (форк от
`origin/develop`, коммит `add7d45`). Трекер (этот файл, claim/resolved) — на docs-ветке
`release/1.1-planning-daily-ux-docs`, как принято в проекте.

**Что сделано (все 5 пунктов «Что сделать»):**

1. `main.js` — добавлен маршрут `{ path: '/load-plan', component: LoadPlanView, meta: { requiresAuth: true } }`
   (+ импорт `LoadPlanView`). Создан `web/src/components/LoadPlanView.vue` — тонкая
   обёртка-страница: `page-header` c `<h1>План нагрузки</h1>` + eyebrow «Планирование ·
   нагрузка по Проектам и Рутинам», ниже `<LoadCharts/>` (четыре вкладки над единым
   источником load-charts).
2. `PlanningView.vue` (`/roadmap`) — встроенная `<LoadCharts/>` убрана; вместо неё
   навигационная ссылка «Открыть План нагрузки →» на `/load-plan` (без дублирования
   диаграмм). Страница теперь = только Гантт + ссылка.
3. Совместимость якоря: navigation guard в `main.js` — `/roadmap#load-plan` →
   redirect на `/load-plan` (302-аналог для hash-history SPA), query пробрасывается,
   так что deep-link `?chart=<key>` сохраняется. В `LoadCharts.setTab` базовый путь
   `'/roadmap'` заменён на `'/load-plan'` (вид пишется в query без перезагрузки данных).
4. `App.vue` — пункт меню «План нагрузки»: `to: '/roadmap#load-plan'` → `to: '/load-plan'`.
5. Пункт «Дорожная карта» (`/roadmap`) сохранён — теперь показывает только Гантт.

Дополнительно: из `LoadCharts.vue` убрана внутренняя `section-heading` (дублирующая
`<h2>План нагрузки</h2>` + eyebrow «Нагрузка») — заголовок теперь даёт страница-обёртка,
иначе была бы двойная шапка (антипаттерн `bugs/01`); удалён ставший мёртвым CSS
`.section-heading`. Логика `isChildActive`/`baseOf` в `App.vue` корректно подсвечивает
пункт при новом пути без изменений.

**Как проверено (headless, без браузера):**
- `cd web && npm run build` — зелёный (vite, 119 модулей).
- Греп собранного бандла: маршрут/guard `/load-plan` присутствуют; склеенной строки
  `roadmap#load-plan` в шаблонах нет (guard сравнивает `to.path` и `to.hash` раздельно).
- Vite SSR-рендер `LoadPlanView`: `<h1>План нагрузки</h1>` + `page-header` + eyebrow
  присутствуют; `LoadCharts` монтируется (`section#load-plan`, скелет «Загрузка
  диаграмм…» из onMounted-фетча); дублирующей `<h2>План нагрузки</h2>` нет.

**Что НЕ проверено (браузер недоступен — ручная приёмка, п. Testing Decisions):**
- Живой переход по меню «План нагрузки» → `/load-plan` со всеми вкладками.
- Живой редирект `/roadmap#load-plan` → `/load-plan` в рантайме.
- `/load-plan?chart=ladder` открывает вкладку «Лестница» без перезагрузки данных.
- `/roadmap` рендерит Гантт без встроенной секции и без её дубля.

Мерж в `develop` и передеплой `wolf-web` — по договорённости (см. skill
`wolf-web-release-dev`, references/merge-and-deploy-via-worktree.md). После мержа снимается
блокировка с тикетов 03 и 04.

