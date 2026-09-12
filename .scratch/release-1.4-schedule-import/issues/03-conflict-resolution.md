# 03: Разрешение конфликтов при пересекающемся импорте

**What to build:** Когда я импортирую файл, часть недель которого уже загружена (обновил
таблицу, дописал август и гружу заново), система не пишет молча и не плодит дубли: показывает,
сколько ячеек конфликтует, и даёт один осознанный выбор на весь импорт.

**Blocked by:** 02

**Status:** resolved (ветка `release-1.4/feature/03-conflict-resolution`, коммит `36f5ff8`)

> **Связь с изменённым контрактом хранения:** конфликт по-прежнему определяется на уровне
> исходной 15-минутной ячейки, но финальный `apply` должен материализовать свободные участки
> агрегированными Записями из тикета 07. При `SKIP_ALL` агрегированная запись не пересекает
> занятую и пропущенную ячейку; при `OVERWRITE_ALL` заменённая ячейка может войти в общий
> непрерывный интервал.

- [x] Конфликт = **ячейка уже занята** любой Записью времени (без сравнения активностей)
- [x] В preview-сводке (тикет 02) показывается число конфликтующих ячеек (`ImportPreviewResponse.conflictingCells`)
- [x] При «Применить» с конфликтами — **один выбор на весь импорт**: пропустить все / перезаписать все / отменить
- [x] «Пропустить все» (SKIP_ALL, дефолт) — занятые ячейки не трогаются, создаются только свободные
- [x] «Перезаписать все» (OVERWRITE_ALL) — существующая Запись в конфликтной ячейке заменяется данными из файла на месте (без дублей)
- [x] «Отменить» (CANCEL) — ничего не пишется, `importRunId = null`
- [x] Поячеечных вопросов по конфликтам НЕ вводить (осознанно вне объёма)
- [x] `*ApiIT`: импорт поверх занятых слотов для каждой из трёх веток (skip/overwrite/cancel) даёт ожидаемое состояние; без дублей в ячейке

## Answer

Реализовано отдельным перечислением `ImportConflictStrategy` (SKIP_ALL / OVERWRITE_ALL / CANCEL)
и расширением `XlsxSchedulePreviewService`:

- `preview()` теперь считает `conflictingCells` — слоты, уже покрытые любой Записью времени
  пользователя (`findByUserIdAndStartAt`), без записи в БД.
- `apply(...)` принимает стратегию: SKIP_ALL (дефолт, обратно совместим с тикетом 02) оставляет
  существующий факт и считает `skippedOccupied`; OVERWRITE_ALL заменяет Запись времени и Дело
  на месте и считает `overwritten`; CANCEL ничего не пишет (`cancelled = true`, `importRunId = null`).
- Контроллер `POST /import/xlsx/apply` принимает `conflictStrategy` как multipart-поле
  (дефолт SKIP_ALL), эндпоинт остаётся `multipart/form-data`.
- `ImportPreviewResponse.conflictingCells`, `ImportApplyResponse.skippedOccupied` /
  `overwritten` / `cancelled` — DTO-поля добавлены без поломки старых.
- UI `XlsxImportView`: в сводке предпросмотра показывается число конфликтующих ячейек; при
  конфликте (>0) — один подтверждающий выбор пропустить/перезаписать/отменить (через `window.confirm`,
  без поячеечных вопросов).

**Числа (реальные прогоны):** `XlsxSchedulePreviewApiIT` 11/11 зелёно на реальном PostgreSQL
(Testcontainers) — 8 тестов из тикета 02 плюс 3 новых: `preview_counts_conflicting_cells_without_writing`,
`overwrite_all_replaces_existing_entries_with_file_data`, `cancel_writes_nothing_and_leaves_existing_facts_intact`.
`npm run build` зелёный.

> Ветвление: код — на `release-1.4/feature/03-conflict-resolution` (стек поверх 02). Тикет-доки —
> на ветке `feature/xlsx-schedule-import-docs`, как принято в релизе (см. коммит `b2cc6df`).
> Локальный `develop` отстаёт от `origin/develop` — тикеты 01/02 уже влиты в `origin/develop`,
> здесь не видны в рабочем дереве.
