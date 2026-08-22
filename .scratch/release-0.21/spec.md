# WOLF Release 0.21 — Синхронизация данных

Status: `ready-for-agent`
Feature slug: `release-0.21`
Tracker: local markdown
Depends on: `release-0.2` data model and existing import flows
ADR: `docs/adr/0004-data-synchronization-backup-restore.md`

---

## Problem Statement

WOLF уже содержит пользовательские данные в нескольких сущностях и несколько несовместимых импортных потоков: расписание Дел, XLSX-историю, Проекты, Идеи, Заметки и связанные структуры. Пользователь не может получить единый переносимый снимок своих данных, проверить чужой файл до загрузки или безопасно синхронизировать изменения между окружениями.

Нужен единый пользовательский контракт данных: экспортируемый в таблицу, читаемый человеком и пригодный для повторного импорта без потери связей, статусов и временных записей.

## Solution

Релиз 0.21 добавляет полноценный backup/restore и точечную синхронизацию пользовательских данных:

- один XLSX-workbook с листом `manifest` и отдельными листами сущностей;
- экспорт всех поддерживаемых пользовательских данных в стабильном версионированном формате;
- preview импорта без записи: обнаруженные строки, ссылки, create/update/skip/delete и построчные ошибки;
- явное атомарное `apply` только после preview;
- upsert по стабильным `externalId`, без удаления отсутствующих данных по умолчанию;
- отдельный явно включаемый режим удаления отсутствующих сущностей в выбранных листах;
- импорт подготовленного расписания, проектов, идей, заметок и связанных данных;
- сохранение плановых и фактических Записей времени с их статусами;
- UI WOLF для скачивания экспорта, загрузки XLSX, просмотра preview и применения импорта;
- сохранение legacy-потоков XLSX сетки и CSV Дел до завершения миграции, но новый workbook становится каноническим полным backup-форматом.

Система не переносит пароли, JWT, служебные логи агента и бинарные аудиофайлы заметок. Для заметок экспортируются ссылки/метаданные и текстовая транскрипция.

## User Stories

1. Как Пользователь, я хочу выгрузить все мои пользовательские данные одним XLSX, чтобы восстановить их или перенести в другое окружение.
2. Как Пользователь, я хочу видеть manifest с версией формата и составом листов, чтобы файл был самодостаточным.
3. Как Пользователь, я хочу импортировать расписание с датами, временем, проектами и Областями жизни, чтобы не создавать тысячи Дел вручную.
4. Как Пользователь, я хочу импортировать Проекты, Рутины, Идеи, Заметки, Цели, Синергии, зависимости, бэклоги и чек-листы вместе со связями.
5. Как Пользователь, я хочу сначала получить preview импорта, чтобы увидеть ошибки и последствия до записи.
6. Как Пользователь, я хочу применять валидный preview атомарно, чтобы ошибка не оставляла частично загруженные данные.
7. Как Пользователь, я хочу повторно импортировать экспортированный файл без дублей, чтобы backup/restore был идемпотентным.
8. Как Пользователь, я хочу, чтобы совпадения обновлялись по externalId, а не по названию, чтобы переименование не создавало вторую сущность.
9. Как Пользователь, я хочу отдельно включать удаление отсутствующих записей, чтобы обычная синхронизация ничего случайно не удаляла.
10. Как Пользователь, я хочу экспортировать и импортировать плановые и фактические записи времени со статусами, чтобы история оставалась честной.
11. Как Пользователь, я хочу управлять импортом через UI, а не вручную вызывать API.

## Contract

### Workbook

- Формат: XLSX, UTF-8 в текстовых ячейках, RFC-подобная семантика полей.
- Обязательный лист `manifest`: `format=wolf-data`, `version=0.21`, generatedAt, user-independent schema metadata, список листов и row counts.
- Листы: `life_areas`, `life_spheres`, `projects`, `routines`, `routine_schedules`, `delos`, `time_entries`, `goals`, `goal_metrics`, `goal_week_budgets`, `ideas`, `notes`, `synergies`, `project_dependencies`, `backlog_items`, `checklist_items`, `activity_mappings`.
- Каждая сущность содержит стабильный `externalId`; связи используют externalId, а не внутренние database id.
- Даты — `YYYY-MM-DD`; локальные дата-времена — ISO `YYYY-MM-DDTHH:mm` без изменения пользовательского timezone; интервалы полуоткрытые `[startAt,endAt)`.
- Списки ссылок используют `|`; сложные значения вроде milestones — JSON-текст в одной ячейке.

### Import semantics

- Preview сначала полностью разбирает и валидирует workbook, затем строит план изменений.
- Apply принимает только идентификатор успешно созданного preview и его checksum.
- `upsert` — режим по умолчанию: create для новых externalId, update для существующих, skip для неизменённых.
- Отсутствующие строки не удаляются по умолчанию.
- `deleteMissing=true` допустим только с явным списком листов/сущностей и отражается в preview.
- Любая ошибка ссылок, дат, enum, ownership, циклов или коллизий делает apply недоступным; apply атомарен.
- Дубликаты externalId внутри файла и ссылки на неизвестные externalId — ошибки.
- Existing internal ids никогда не импортируются как authoritative identifiers.
- Legacy `POST /api/v1/delos/import` и `POST /api/v1/import/xlsx` остаются совместимыми; новый flow должен уметь принять подготовленное расписание в листах `delos` + `time_entries`.

## API Shape

- `GET /api/v1/data-sync/export?format=xlsx&version=0.21` — скачать полный workbook.
- `GET /api/v1/data-sync/manifest?version=0.21` — получить контракт и шаблоны.
- `POST /api/v1/data-sync/import/preview` — multipart XLSX, вернуть preview id, checksum, summary, row errors и planned changes.
- `GET /api/v1/data-sync/import/{id}/preview` — получить preview.
- `POST /api/v1/data-sync/import/{id}/apply` — применить preview; body содержит `deleteMissing` и выбранные scopes.
- `GET /api/v1/data-sync/import/{id}/result` — получить результат применения.

## Implementation Decisions

- Spring Boot monolith, Vue SPA, PostgreSQL, Apache POI; без отдельного сервиса и message broker.
- Domain import service отделён от XLSX reader/writer и от legacy import controllers.
- Preview хранит checksum, исходный файл/нормализованный staging и срок действия; apply повторно проверяет checksum.
- Внешние ключи разрешаются в два прохода: сначала сущности, затем связи; циклические зависимости проектов валидируются до записи.
- Время импортируется после Дел/Рутин/Проектов; статусы `PLANNED`, `DONE`, `UNKNOWN` сохраняются явно.
- Ошибки возвращаются с листом, номером строки, полем и понятным сообщением.
- Экспорт ограничен authenticated user и никогда не включает секреты.

## Testing Decisions

- API integration tests через WebTestClient + Testcontainers PostgreSQL.
- Round-trip: создать полный набор сущностей → export → preview → apply в чистом user scope → export; сравнить нормализованные листы и связи.
- Preview не меняет базу.
- Невалидная ссылка, duplicate externalId, плохая дата/enum, overlap/duplicate time slot и cycle дают построчную ошибку и запрещают apply.
- Upsert повторного файла не создаёт дубли и обновляет изменённые поля.
- deleteMissing по умолчанию выключен; включённый scope удаляет только явно разрешённый тип.
- Legacy CSV/XLSX import tests продолжают проходить.
- UI smoke: скачивание XLSX, загрузка, preview summary, ошибки, disabled/enabled Apply, успешный результат.

## Out of Scope

- Live OAuth-синхронизация с Google/Outlook/Apple Calendar.
- Синхронизация между аккаунтами и multi-user sharing.
- Экспорт бинарных аудиофайлов заметок и секретов.
- Автоматическая фоновая синхронизация, cron, webhooks и push-уведомления.
- Импорт произвольных SQL/JSON dumps.
- Молчаливое объединение записей только по названию или времени.

## Delivery Order

1. Contract, manifest, external-id strategy and XLSX reader/writer.
2. Export all supported user-owned entities.
3. Preview validation and change plan.
4. Atomic upsert apply, optional scoped delete and result.
5. Prepared schedule/projects/ideas/notes fixtures and compatibility tests.
6. UI and real browser verification.
7. Full round-trip and legacy regression gate.

## Further Notes

- Полный backup — не то же самое, что экспорт отчёта: он должен быть повторно импортируемым.
- Таблица должна быть читаемой человеком, но machine-readable semantics важнее визуального форматирования.
- «Все пользовательские данные» означает все перечисленные доменные сущности, но не секреты и не служебные журналы.
- Тихая система сохраняется: импорт показывает факты и последствия, но не оценивает пользователя и не создаёт тревожные уведомления.
- Тикеты релиза лежат в `.scratch/release-0.21/issues/` и нумеруются вертикальными срезами.
