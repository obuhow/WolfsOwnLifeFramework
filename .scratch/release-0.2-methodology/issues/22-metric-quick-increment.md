# 22 — Метрики Целей: быстрый инкремент (+1/+5) + произвольный ввод

**What to build:** UI/API расширение метрик Целей (тикет 02) — кнопки быстрого инкремента `+1`, `+5` и произвольный ввод значения рядом с каждой метрикой. Дополняет `POST /goals/{id}/metrics`, добавляя удобный способ обновления «сейчас» без ручного ввода числа.

**Примеры использования:**
- Метрика «отправлено заявок» (к Цели «Доход 40к+»): +1 за каждую отправленную заявку.
- Метрика «вес, кг» (к Цели «Вес 87»): произвольный ввод при взвешивании.
- Метрика «готовность трека, %» (к Цели «Трек с DJ»): +5 за каждый завершённый этап.

**Конфликт с тикетом 02:** тикет 02 реализует метрики Целей (CRUD + история + факт). Тикет 22 добавляет UX быстрого инкремента поверх (`PATCH /goals/{id}/metrics/{metricId}/increment`). **Не отменяет, не переделывает тикет 02 — расширяет.**

**Blocked by:** 02 — Цели (метрики уже существуют; добавляем инкремент).

**Status:** resolved

## Verification

- `GoalMetricIncrementApiIT` passed on the feature and merged `develop` checkouts.
- Clean Java compile/test-compile, frontend build, `git diff --check`, and Compose config passed.
- Browser/DOM verification skipped under the explicit project instruction.

- [x] `PATCH /api/v1/goals/{id}/metrics/{metricId}/increment {amount}` — атомарный инкремент (current_value += amount)
- [x] UI: рядом с каждой метрикой на странице Цели — кнопки `+1`, `+5` и поле произвольного ввода
- [x] История значений метрики (из тикета 02) пишет запись при каждом инкременте
- [x] Валидация: нельзя инкрементнуть ниже 0 или выше target_value (если target_value задан), 400 с пояснением
- [x] API test: создать метрику «заявки» current=3, target=500, increment(+1) → current=4, increment(+5) → current=9
- [x] API test: increment ниже 0 → 400
- [x] API test: increment выше target → 400