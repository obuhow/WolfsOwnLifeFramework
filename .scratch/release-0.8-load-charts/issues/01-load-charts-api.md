# Бэкенд диаграмм нагрузки: настройка `hoursPerDelo` и `GET /planning/load-charts`

Status: claimed
Blocked by:
Blocks: 02, 03, 04, 05

## Question

Дать всем четырём диаграммам нагрузки один источник чисел. Четыре вкладки обязаны показывать согласованные объёмы, остатки и прогнозы — формула живёт на бэкенде в одном месте, не дублируется в JS каждой вкладки.

## Что сделать

1. **Миграция.** Новая колонка `user.hours_per_delo` — `NUMERIC(5,2) NOT NULL DEFAULT 1.5`. Перед созданием файла сверить свободный номер: `ls api/src/main/resources/db/migration | sed 's/^V\([0-9]*\)__.*/\1/' | sort -n | tail -1` (на момент написания спеки — 38, значит `V39__hours_per_delo.sql`).
2. **Поле `hoursPerDelo`** в `User` (`java.math.BigDecimal`, default `1.5`), в ответе и запросе настроек пользователя. Валидация: строго больше нуля. Подпись в UI настроек — «Часов на одно Дело», рядом с «Доступно часов в неделю» (сам UI — часть этого тикета, поле простое).
3. **`GET /api/v1/planning/load-charts`** — новый эндпоинт. Параметры: `horizonMonths` (default 18), `lifeAreaIds` (опционально, тот же формат, что на Гантте). Структура ответа — дословно по разделу «Новый API» спеки релиза (`weeklyLimit`, `hoursPerDelo`, `hourAccountingMode`, `horizonMonths`, `startMonday`, `projects[]`, `routines[]`, `monthlyLoad[]`).
4. **Расчёт объёма** (`effortHours` + `effortSource`): `project.totalPlanHours`, если задан и больше нуля → `effortSource: PLAN`; иначе `deloCount × user.hoursPerDelo` → `effortSource: ESTIMATED`. `deloCount` считается по `delo_project` с учётом режима учёта часов: `PRIMARY_ONLY` — только Дела, где Проект основной; `ALL_PROJECTS` — все прикреплённые.
5. **Накопленный факт** — переиспользовать способ агрегации из `GanttForecastService.aggregateProjectFact` (весь факт от эпохи до «сейчас», режим учёта часов соблюдается). Не писать вторую формулу подсчёта факта. `remainingHours = max(0, effortHours − accumulatedFactHours)`.
6. **Два прогноза на проект:**
   - `forecastByRate` — по текущей ставке: `weeks = remainingHours / weeklyPlanHours`, финиш = ближайший понедельник + `ceil(weeks)` недель; при ставке 0 → `null`. `weeklyPlanHours` берётся из `WeekPlan` текущей ISO-недели, при отсутствии — 0.
   - `forecastByCurve` — по кривой: помесячное накопление, часы месяца = `hoursAt(curve, month) × 4.33`; финиш — первый месяц, где накоплено ≥ `remainingHours`; не уложились в горизонт → `null`.
7. **`monthlyLoad`** — суммарная загрузка по месяцам горизонта: часы проектов (по кривым, при отсутствии кривой — по ставке `WeekPlan`) плюс `weeklyHours` активных Рутин. Флаг `overLimit` = сумма выше `weeklyLimit` (`user.availableWeeklyHours`).
8. **Отбор проектов** — статус `IN_PROGRESS`, как в существующем `PlanningCapacityController`. Рутины идут отдельным массивом `routines[]` и участвуют только в `monthlyLoad` — дорожек и прогноза финиша не имеют.
9. **`GET /api/v1/planning/capacity` не трогать.** Существующий эндпоинт остаётся с прежним контрактом — на него завязаны три зелёных IT-класса.

## Testing Decisions

- `LoadChartsApiIT`: три проекта — с `totalPlanHours` (`effortSource: PLAN`), без него но с Делами (`ESTIMATED`, `effortHours == deloCount × hoursPerDelo`), пустой (`effortHours: 0`, оба прогноза `null`).
- `LoadChartsApiIT`: `remainingHours` не бывает отрицательным; факт больше объёма → ноль и финиш = текущий понедельник.
- `LoadChartsApiIT`: переключение `hourAccountingMode` на `ALL_PROJECTS` меняет `deloCount` и `accumulatedFactHours` у Дела, прикреплённого к двум проектам.
- `LoadChartsApiIT`: `monthlyLoad` включает часы рутин; месяц выше нормы помечен `overLimit: true`, ниже — `false`.
- `LoadChartsApiIT`: проект с кривой «16 ч/нед, затем 8 с пятой недели» и закрываемым объёмом даёт непустой `forecastByCurve`; кривая в 0 часов → `null`.
- `LoadChartsApiIT`: изоляция арендатора — чужие проекты и кривые в ответе отсутствуют.
- `UserSettingsApiIT` (дополнение): `hoursPerDelo` сохраняется, отвергается при значении ≤ 0, у нового пользователя равен 1.5.
- Существующие `LoadCurveApiIT`, `PlanDistributionApiIT`, `RoutineApiIT` проходят **без правок их кода**.
- Прогон по одному классу за раз (`--tests "*.LoadChartsApiIT"`), согласно принятой в проекте стратегии.

Status: resolved

## Answer

Реализовано и покрыто тестами. Ветка `release-0.8/feature/01-load-charts-api`, коммит `9976ffe`.

1. **Миграция** — `V41__hours_per_delo.sql` (V39/V40 уже были заняты в develop на момент работы; номер сверен перед созданием). Колонка `user.hours_per_delo NUMERIC(5,2) NOT NULL DEFAULT 1.5`.
2. **`User.hoursPerDelo`** (`BigDecimal`, default `1.5`), проброшен в `UserSettingsResponse`/`UpdateSettingsRequest` с валидацией `@DecimalMin(inclusive=false)` (> 0). У нового пользователя = 1.5.
3. **`GET /api/v1/planning/load-charts`** — новый `LoadChartsController` + `LoadChartsService` (по текущей конвенции, без слоистой архитектуры). Параметры `horizonMonths` (default 18), `lifeAreaIds` (CSV, как на Гантте). Структура ответа — дословно по спеки (`weeklyLimit`, `hoursPerDelo`, `hourAccountingMode`, `horizonMonths`, `startMonday`, `projects[]`, `routines[]`, `monthlyLoad[]`).
4. **Объём** — `totalPlanHours` (если > 0) → `effortSource: PLAN`; иначе `deloCount × hoursPerDelo` → `ESTIMATED`. `deloCount` по `delo_project` с учётом `hourAccountingMode` (`PRIMARY_ONLY` → только основные, `ALL_PROJECTS` → все).
5. **Накопленный факт** — переиспользована формула из `GanttForecastService` (добавлен публичный `accumulatedFactByProject` + расширен `aggregateProjectFact` до public), **без второй формулы**. `remainingHours = max(0, effort − fact)`.
6. **Прогнозы** — `forecastByRate` (по `WeekPlan` текущей ISO-недели; ставка 0 или объём 0 → `null`) и `forecastByCurve` (помесячное накопление `hoursAt × 4.33`; первый месяц с накоплением ≥ remaining → финиш; не уложились → `null`).
7. **`monthlyLoad`** — часы проектов (по кривой, без кривой — по ставке `WeekPlan`) + `weeklyHours` рутин; `overLimit` = сумма > `availableWeeklyHours`.
8. **Отбор** — `IN_PROGRESS` (как в `PlanningCapacityController`); рутины отдельным массивом, без дорожек/финиша.
9. **`GET /planning/capacity` не тронут.**

Тесты: `LoadChartsApiIT` (6 сценариев из тикета) — зелёный; `UserSettingsApiIT` дополнен (hoursPerDelo сохраняется/валидируется/по умолчанию 1.5) — зелёный; обратная совместимость `LoadCurveApiIT`, `PlanDistributionApiIT`, `RoutineApiIT` — зелёные (без правок их кода).

Примечание: в `RoutineApiIT` при массовом прогоне наблюдался `Connection refused` от Testcontainers (docker flaky на слабом хосте) — при отдельном прогоне класс зелёный; не связано с изменениями тикета.
