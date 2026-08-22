# 20 — Рутины: сущность + CRUD + недельная квота + повторяющееся расписание

**What to build:** Рутина — постоянный процесс жизни с недельной квотой часов и повторяющимся расписанием (сон, еда, гигиена, спорт, танцы, зал). Отличается от Проекта (конечная инициатива с датами и целями) и от Идеи (ещё не проект). В отличие от Дела (шаблон активности), Рутина резервирует часы из недельного пула автоматически и определяет повторяющееся расписание на постоянной основе.

**Сущность «Рутина» — отдельная от Проекта и Идеи:**
- Недельная квота часов (обязательное поле).
- Интервал(ы) повторения (дни недели + время начала/конца).
- Цвет/иконка (визуальное отличие в сетке).
- Связь с Целью (many-to-many, как и Проект) — Рутина служит Цели.
- Синергия со Сферами жизни (из тикета 01).
- Рутина ≠ Дело без проекта (Дело без проекта — валидная рутина/личное, но не является первой-классной сущностью с квотой).

**Поведение:** часы Рутины автоматически вычитаются из доступного недельного пула и отображаются в суммарной плановой нагрузке (капасити, тикет 18) нейтральной дельтой. Никаких красных предупреждений при превышении — только факт и дельта.

**Blocked by:** None — can start immediately.

**Status:** resolved

- [x] Таблица `routine` (id, user_id, title, description, weekly_hours, color, created_at)
- [x] Таблица `routine_schedule` (routine_id, day_of_week, start_time, end_time) — повторяющееся расписание
- [x] Таблица `routine_goal` — связь Рутина ↔ Цель (many-to-many)
- [x] CRUD `/api/v1/routines` (create, list, get, update, archive)
- [x] Связь Рутины с Целями: `POST/DELETE /routines/{id}/goals/{goalId}`
- [x] Синергия Рутин со Сферами жизни: расширение `/api/v1/synergies` на routineId (null-able, как ideaId)
- [x] Автоматический учёт недельной квоты Рутины в суммарной плановой нагрузке (capacity endpoint)
- [x] UI: страница Рутин (список + создание/редактирование)
- [x] UI: управление связями Рутина–Цель и Рутина–Сфера жизни
- [x] API tests: CRUD, расписание, связь с Целью, Синергия, квота в capacity, отрицательная квота и невалидный интервал

## Answer

- Feature commit: `bb61bc3` — `feat(20): add routines and capacity quota`
- Browser/DOM verification: intentionally skipped by explicit user instruction; runtime smoke completed instead.
- API: `./gradlew test --tests 'ru.wolf.api.routine.RoutineApiIT' --no-daemon` — passed.
- Compatibility API: `./gradlew test --tests 'ru.wolf.api.lifesphere.SynergyApiIT' --no-daemon` — passed.
- Frontend: `npm ci` and `npm run build` — passed.
- Compose: `docker compose config --quiet` — passed.
- Runtime: isolated stack `wolf20-db`, `wolf20-api`, `wolf20-web` started; API `/actuator/health` returned `{"status":"UP"}`; Flyway applied migration V22.

## Comments

- 2026-08-21: Delivered in isolated `feature/20-routines` worktree and verified before merge.

## End

