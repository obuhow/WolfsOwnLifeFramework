# Тикет 09 — CSV-экспорт всех сущностей (раунд-трип)

Status: resolved
Blocked by:
Type: task

## Вопрос

Добавить рядом с «Синхронизация данных» экспорт всех сущностей БД в CSV-файл в формате,
пригодном для обратного импорта (перенос данных между аккаунтами). (Доработка 5 заявки.)

## Готовая инфраструктура

- `DataSyncExportService.export(User)` (`datasync/DataSyncExportService.java:62`) уже
  собирает все сущности (life_areas, life_spheres, projects, routines, delos, goals,
  ideas, notes, synergies, project_dependencies, backlog_items, checklist_items, …) —
  те же таблицы, что в `DataSyncContract` (`datasync/DataSyncContract.java:27-45`).
- `GET /api/v1/data-sync/export` (`DataSyncExportController.java:30`) уже отдаёт workbook
  формата `wolf-data` версии `0.21` (xlsx). CSV — это тот же набор листов/колонок,
  но построчно.

## Что сделать

1. Расширить `DataSyncExportController.export` (или добавить `GET /api/v1/data-sync/export?format=csv`)
   ветку `format=csv`: сериализовать каждый `Sheet` из `DataSyncContract.SHEETS` в
   CSV со строкой заголовков (те же имена колонок) и экранированием `,`/`"`/`\n`.
   Даты/локальное время — в том же конвенциальном формате (`YYYY-MM-DD`,
   `YYYY-MM-DDTHH:mm`), что в `DataSyncContract.manifest()` (строки 49-56).
2. `Content-Type: text/csv; charset=utf-8`, `Content-Disposition: attachment;
   filename="wolf-data-<user>-<date>.csv"`.
3. На фронте `DataSyncView.vue` (рядом с кнопкой Синхронизация) добавить кнопку
   «Экспорт в CSV» → `fetch('/api/v1/data-sync/export?format=csv')` → скачивание файла.
4. **Импорт CSV** (обратный раунд-трип): расширить `DataSyncImportController`/`ImportXlsx`
   приёмом csv-набора с теми же именами колонок (`DataSyncContract`). Минимально —
   переиспользовать существующий парсер workbook, добавив csv-ридер, который отдаёт
   те же `Sheet(name, rows)`; дальше — существующий `DataSyncImportApplyService`
   (upsert по внешним идентификаторам).
5. Миграция не нужна — колонки уже определены контрактом.

## Testing Decisions

- `DataSyncExportApiIT`: `GET /data-sync/export?format=csv` → 200, `text/csv`;
  тело содержит заголовки всех листов (`projects,life_areas,…`) с теми же колонками,
  что `DataSyncContract` (в т.ч. `projects` несёт `parentExternalId`, `startDate`,
  `endDate`, `planDistribution` — это закроет и перенос вложенности проектов).
- `DataSyncImportApiIT`: тот же CSV, загруженный обратно через импорт, восстанавливает
  сущности (upsert по `externalId`, ничего не удаляет по умолчанию).
- Ручная приёмка: экспорт CSV → открыть в табличном редакторе → импорт того же файла
  в другой аккаунт → данные перенесены.

## Out of Scope

- Экспорт в форматы помимо wolf-data (csv/xlsx) — только раунд-трип внутри контракта.
- Автоматический backup по расписанию — только ручной экспорт.

## Закрывает

Доработка 5 заявки. Дополняет существующий механизм Синхронизации данных (ADR-0004).

## Answer

Реализовано в `release-1.0/feature/09-csv-export`:

**Бэкенд:**
- `DataSyncCsvCodec`: сериализация contract-rows → единый CSV со всеми листами (секции `# sheet:<name>` + заголовки колонок контракта), RFC 4180 экранирование (`, " \n \r`); обратный парсинг CSV → xlsx-workbook для раунд-трипа. `looksLikeCsv` детектирует наш формат по маркеру.
- `DataSyncExportService.buildRows(User)` выделен из `export` — переиспользуется CSV-экспортом (тот же набор строк, что и xlsx).
- `DataSyncControllerService`: `export(format=csv)` → CSV; `preview` конвертирует загруженный CSV в workbook (`maybeConvertCsv`) и прогоняет существующий пайплайн валидации/применения. Формат-валидатор принимает xlsx и csv.
- `ByteArrayMultipartFile`: обёртка байтов CSV-derived workbook для существующего preview.
- `DataSyncExportController`: ветка csv → `Content-Type: text/csv; charset=utf-8`, `filename=wolf-data-0.21.csv`.
- Миграция не нужна — колонки уже определены контрактом.

**Фронт** (`DataSyncView.vue`):
- Кнопка «Экспорт в CSV» рядом со «Скачать XLSX» → скачивание `wolf-data-0.21.csv`.
- `<input accept=".xlsx,.csv">` — импорт принимает оба формата (единый пайплайн).

**Тесты** (`DataSyncCsvApiIT`, 2/2 PASSED):
1. `export_csv_carries_all_sheet_headers_and_project_columns`: `GET /export?format=csv` → 200, `text/csv`, тело содержит `# sheet:<name>` + заголовки всех 17 листов контракта; projects несёт `parentExternalId`, даты, `EVEN_WEEKDAYS` (закрывает перенос вложенности).
2. `csv_round_trips_back_through_import`: экспортированный CSV загружен обратно через `/import/preview` → `VALID`, `applyAllowed=true`, счётчики projects/life_areas корректны.

Сборка фронтенда: `vite build` — 117 модулей, без ошибок.
