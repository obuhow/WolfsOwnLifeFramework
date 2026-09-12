# 01 — Контракт wolf-data 0.21 и manifest

Status: `ready-for-agent`
Type: task
Blocked by: None

## What to build

Зафиксировать машинно-читаемый контракт workbook 0.21 и общий слой externalId. Создать manifest с version, schema, sheet names, columns and row counts. Добавить формат endpoint, шаблоны листов и нормализованные fixture-файлы для всех поддерживаемых сущностей.

## Acceptance criteria

- `GET /api/v1/data-sync/manifest?version=0.21` возвращает контракт и шаблоны.
- Manifest workbook содержит `format=wolf-data`, `version=0.21`, список листов и counts.
- Для каждой сущности определены обязательные поля, enum, даты, ссылки и правила списков/JSON.
- ExternalId уникален внутри типа сущности и не зависит от DB id.
- Legacy import contracts не ломаются.

## Tests

- Contract tests for manifest and every sheet header.
- Reject duplicate externalId and unknown columns where contract forbids them.
- Fixture parses with Apache POI and round-trips normalized values.

## Out of scope

Чтение/запись пользовательских данных и UI.

## Comments

Контракт подтверждён для полного XLSX backup/restore, upsert, preview/apply и явного scoped delete.
```
