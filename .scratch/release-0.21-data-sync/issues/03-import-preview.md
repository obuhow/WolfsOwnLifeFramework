# 03 — Preview импорта workbook

Status: `ready-for-agent`
Type: task
Blocked by: 01

## What to build

Реализовать multipart `POST /api/v1/data-sync/import/preview` для XLSX. Полностью разобрать workbook, проверить manifest/version, headers, types, dates, enums, ownership rules, externalId uniqueness, references, project cycles, interval validity and time-entry collisions. Ничего не записывать в доменные таблицы.

Создать preview id, checksum, expiry/staging metadata и change plan: create/update/skip/delete. Ошибки должны содержать sheet, row, field, externalId and message. Preview is invalid when any blocking error exists.

## Acceptance criteria

- Valid workbook returns preview id, checksum and complete summary.
- Invalid references, duplicate externalIds, bad dates/enums and cycles are reported per row.
- No database mutation occurs on preview, including auto-creation of projects/life areas.
- `GET /api/v1/data-sync/import/{id}/preview` returns the same plan.
- Only authenticated user's preview is accessible.

## Tests

- Preview valid full fixture.
- Preview invalid fixture with multiple independent errors.
- Verify domain counts and audit tables remain unchanged.
- Check checksum and preview ownership/expiry.

## Out of scope

Apply mutation and UI.

## Comments

Preview is mandatory before apply; no partial import is allowed.
