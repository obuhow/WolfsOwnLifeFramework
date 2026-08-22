# WOLF data contract v0.2

Этот документ описывает расширенный формат импорта и экспорта, добавленный в Release 0.2.

## Контейнеры

- CSV: набор секций/файлов `projects`, `routines`, `routine_schedules`, `delos`, `time_entries`, `synergies`, `links`.
- XLSX: один workbook с листами с теми же именами и листом `manifest`.
- Кодировка CSV: UTF-8, quoting по RFC 4180.
- `manifest`: `format=wolf-data`, `version=0.2`.

## Общие правила

Каждая строка сущности имеет `entityType` и стабильный `externalId`. Внешние идентификаторы нужны для связей и повторного импорта; они не являются внутренними идентификаторами базы данных.

Поддерживаемые типы:

- `project`
- `routine`
- `routine_schedule`
- `delo`
- `time_entry`
- `synergy`
- `link`

Связи передаются через внешние ключи: `projectId`, `routineId`, `goalId`, `lifeAreaId`, `lifeSphereId`, `deloId`.

Импорт выполняется через `preview` и затем явный `apply`. Preview ничего не записывает. Ошибка любой строки отменяет весь apply. Отсутствующие ссылки не угадываются и не создаются молча.

## Поля сущностей

### Projects

| Поле | Обязательное | Описание |
|---|---:|---|
| `entityType` | да | `project` |
| `externalId` / `projectId` | да | стабильный ключ |
| `name` | да | название Проекта |
| `description` | нет | описание |
| `lifeAreaIds` | нет | внешние ключи Областей жизни |
| `parentProjectId` | нет | внешний ключ родительского Проекта |
| `startDate` | нет | `YYYY-MM-DD` |
| `endDate` | нет | `YYYY-MM-DD` |
| `goal` | нет | текстовая цель Проекта |
| `milestones` | нет | список/JSON milestones |
| `totalPlanHours` | нет | неотрицательное число |
| `goalIds` | нет | внешние ключи Целей |

### Routines

| Поле | Обязательное | Описание |
|---|---:|---|
| `entityType` | да | `routine` |
| `externalId` / `routineId` | да | стабильный ключ |
| `name` | да | название Рутины |
| `description` | нет | описание |
| `weeklyHours` | да | недельная квота, `>= 0` |
| `color` | нет | цвет UI |
| `icon` | нет | идентификатор иконки |
| `goalIds` | нет | внешние ключи Целей |
| `lifeSphereIds` | нет | Сферы жизни для Синергии |
| `archived` | нет | `true/false` |

### Routine schedules

| Поле | Обязательное | Описание |
|---|---:|---|
| `routineId` | да | внешний ключ Рутины |
| `dayOfWeek` | да | `MONDAY` … `SUNDAY` |
| `startTime` | да | `HH:mm` |
| `endTime` | да | `HH:mm`, позже начала |

### Delos

| Поле | Обязательное | Описание |
|---|---:|---|
| `entityType` | да | `delo` |
| `externalId` / `deloId` | да | стабильный ключ |
| `title` | да | название Дела |
| `description` | нет | описание |
| `executionMode` | да | `SELF`, `DELEGATABLE`, `AUTOMATABLE` |
| `projectIds` | нет | внешние ключи Проектов |
| `routineId` | нет | внешний ключ Рутины |
| `lifeAreaIds` | нет | внешние ключи Областей жизни |
| `date` | да | `YYYY-MM-DD` |
| `startAt` | да | `HH:mm` |
| `endAt` | да | `HH:mm` |
| `status` | нет | доменный статус Дела |

### Time entries

| Поле | Обязательное | Описание |
|---|---:|---|
| `entityType` | да | `time_entry` |
| `externalId` / `timeEntryId` | да | стабильный ключ |
| `deloId` | нет | внешний ключ Дела |
| `routineId` | нет | внешний ключ Рутины |
| `projectIds` | нет | внешние ключи Проектов |
| `startAt` | да | дата-время |
| `endAt` | да | дата-время позже начала |
| `status` | да | статус Записи времени |
| `source` | нет | источник импорта/ввода |

## API

- `GET /api/v1/import/format?version=0.2` — manifest и шаблоны.
- `POST /api/v1/import/preview` — проверка файла без записи.
- `POST /api/v1/import/apply` — атомарное применение preview.
- `GET /api/v1/export?format=csv|xlsx&version=0.2` — экспорт набора.

Старый `POST /api/v1/import/xlsx` остаётся отдельным flow для недельной сетки 15-минутных ячеек и не меняет семантику неизвестных активностей и пустых ячеек.

## Пример projects_import.csv

```csv
entityType,externalId,projectId,name,type,lifeAreaIds,parentProjectId,startDate,endDate,goal,milestones,totalPlanHours,goalIds
project,proj_1,proj_1,Обучение профессии QA-инженер,Проект,life_finance,,2025-06-01,2026-09-01,Освоить профессию QA-инженер и автоматизацию тестирования,"Сдать проект JWT (2025-08-24 - 2025-08-28)",120,goal_qa
project,routine_1,routine_1,Бытовые дела,Рутина,life_selfcare,,,2025-06-01,2027-12-31,Поддержание порядка и быта,,,
```

Для полного round-trip fixture используются отдельные строки/листы `routine`, `routine_schedule`, `delo` и `time_entry`; смешивать разные типы в одном листе не рекомендуется.

## Совместимость

Legacy CSV-форматы сохраняются до завершения миграции. Неизвестные колонки не интерпретируются молча. Экспорт не содержит пароли, JWT и другие секреты.
