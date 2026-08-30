# Тикет 09 — CSV-экспорт всех сущностей (раунд-трип)

Status: ready-for-agent
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
