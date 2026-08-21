# 06 — UI синхронизации данных

Status: `ready-for-agent`
Type: task
Blocked by: 02, 03, 04

## What to build

Добавить страницу/раздел WOLF «Синхронизация данных» с кнопкой скачивания полного XLSX, выбором файла, manifest/version, запуском preview, summary create/update/skip/delete, таблицей ошибок `лист / строка / поле / сообщение`, выбором `deleteMissing` и scopes с явным подтверждением, кнопкой Apply и результатом применения.

Apply недоступен при blocking errors или устаревшем preview. API errors показывают status/response body, а не только `Failed to fetch`.

## Acceptance criteria

- Authenticated user can export workbook from UI.
- Upload does not apply automatically.
- Apply is enabled only for valid current preview.
- Destructive scopes are opt-in and visible before apply.
- Large schedule fixture does not freeze the page.
- Existing WOLF register style and quiet-system terminology are preserved.

## Tests

- Component/state tests.
- Browser smoke: export, upload, preview, errors, apply result.
- DOM/accessibility check for file input, summary, Apply and delete controls.

## Out of scope

Live calendar integrations and background sync.

## Done definition

Build, redeploy changed services and authenticated real-browser verification.

## Verification evidence

Not started.

## Answer

Not resolved.

## Status History

- `ready-for-agent`: initial ticket creation.

## Related docs

`.scratch/release-0.21/spec.md`, `docs/adr/0004-data-synchronization-backup-restore.md`.

## End

Status remains `ready-for-agent` until implementation evidence is added.
