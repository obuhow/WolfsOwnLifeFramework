# 02 — Экспорт полного пользовательского workbook

Status: `ready-for-agent`
Type: task
Blocked by: 01

## What to build

Реализовать authenticated `GET /api/v1/data-sync/export?format=xlsx&version=0.21`. Экспортировать все пользовательские сущности релиза: Области жизни, Сферы жизни, Проекты, Рутины и расписания, Дела, Записи времени, Цели/метрики/бюджеты, Идеи, Заметки, Синергии, зависимости, бэклоги, чек-листы и activity mappings.

Каждая строка получает стабильный externalId; связи используют внешние ключи. Плановые и выполненные/unknown Записи времени экспортируются со статусами. Секреты, agent logs и бинарное аудио не экспортируются.

## Acceptance criteria

- Workbook открывается Excel/LibreOffice и содержит manifest + все согласованные листы.
- Экспорт ограничен текущим пользователем.
- Повторная выгрузка не меняет externalId существующих сущностей.
- Даты, timezone, статусы, связи и пустые значения не теряются.
- Content-Disposition предлагает понятное имя файла.

## Tests

- API export smoke and user isolation.
- Fixture assertions for every sheet and cross-sheet links.
- Export does not contain password/JWT/agent log/audio bytes.

## Out of scope

Импорт и UI.

## Comments

Зависит от контракта 01.
