# 04 — Атомарный upsert/apply и scoped delete

Status: `ready-for-agent`
Type: task
Blocked by: 03

## What to build

Реализовать `POST /api/v1/data-sync/import/{id}/apply` и `GET /api/v1/data-sync/import/{id}/result`. Apply принимает только актуальный успешный preview/checksum и применяет workbook атомарно.

Порядок: reference entities → projects/routines/delos/ideas/goals → schedules/time entries/links/notes/synergies/backlogs/checklists. Upsert по externalId; unchanged rows skip. По умолчанию отсутствующие строки не удаляются. `deleteMissing=true` требует явного списка scopes и удаляет только разрешённые пользовательские сущности, с отображением в preview.

## Acceptance criteria

- Valid preview applies all rows in one transaction.
- Any persistence or relationship error rolls back the complete apply.
- Reapplying the same export does not duplicate data.
- Changed fields update existing externalId records.
- Default apply never deletes absent records.
- Scoped delete is explicit, previewed and user-isolated.
- Result reports created/updated/skipped/deleted and links/errors.
- PLANNED/DONE/UNKNOWN time-entry statuses are preserved.

## Tests

- Full round-trip export → preview → apply in clean user scope.
- Repeat apply idempotency.
- Update and rename by externalId.
- Forced failure proves rollback.
- Scoped delete versus default no-delete.
- Preserve time-entry statuses and relationships.

## Out of scope

UI and legacy endpoint changes except compatibility regression tests.

## Comments

This is the main persistence seam of release 0.21. Do not use names or internal database IDs as merge keys. Every created, updated or deleted record remains scoped to the authenticated user.

## Done definition

Focused API tests, round-trip verification and evidence that failed apply leaves no partial persistence.

## Related docs

`.scratch/release-0.21/spec.md`, `docs/adr/0004-data-synchronization-backup-restore.md`.

## Verification evidence

Not started.

## Answer

Not resolved.

## Status History

- `ready-for-agent`: initial ticket creation.

## Safety

No destructive behavior without `deleteMissing` and explicit scopes. Legacy CSV/XLSX endpoints remain operational.

## Dependencies

03 — Import Preview.

## End

Status remains `ready-for-agent` until implementation evidence is added.
