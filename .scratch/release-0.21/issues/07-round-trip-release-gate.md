# 07 — Round-trip, security and release gate

Status: `ready-for-agent`
Type: task
Blocked by: 05, 06

## What to build

Провести полный release gate: export → preview → apply → export, сравнение нормализованных листов, user isolation, large workbook behavior, legacy regression, API/UI smoke и no-secret scan.

## Acceptance criteria

- Round-trip preserves all supported fields, statuses and links.
- Другой пользователь не может читать preview, result или export.
- Invalid apply невозможен; failed apply оставляет базу без частичных записей.
- Relevant API suite and web build pass.
- Running Compose serves current UI assets and authenticated browser verification passes.

## Tests

- Full focused integration suite.
- Real browser DOM verification of synchronization page.
- Export content scan for secrets and service logs.

## Out of scope

Features after the confirmed 0.21 contract.

## Done definition

Release checklist complete and evidence appended here.

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

## Ticket ordering

01 contract → 02 export and 03 preview → 04 apply → 05 fixtures → 06 UI → 07 gate.
