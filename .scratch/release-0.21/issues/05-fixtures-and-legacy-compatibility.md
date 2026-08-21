# 05 — Fixtures расписания, проектов, идей и legacy compatibility

Status: `ready-for-agent`
Type: task
Blocked by: 02, 03, 04

## What to build

Подготовить полные и минимальные XLSX fixtures для реальных сценариев пользователя:

- `Расписание_wolf_import_formatted.csv` → листы `delos` + `time_entries`;
- `projects_import.csv` → `projects` и, при необходимости, `routines`;
- идеи, заметки, цели, синергии, зависимости, бэклоги и чек-листы;
- полный workbook export fixture для round-trip.

Явно проверить отсутствующие проекты/Области жизни, повторяющиеся названия Дел и плановые/фактические статусы. Сохранить legacy CSV/XLSX import behavior.

## Acceptance criteria

- Фикстуры проходят preview без ручного редактирования.
- Расписание импортируется с 15-минутными интервалами, проектами и областями жизни.
- Все связи разрешаются через externalId.
- Повторяющиеся названия Дел не создают лишних сущностей.
- Legacy CSV Delos и старый XLSX grid flow продолжают работать.

## Tests

- Prepared schedule preview and apply.
- Projects/ideas/notes fixture validation.
- Regression tests for `/api/v1/delos/import` and `/api/v1/import/xlsx`.

## Out of scope

Новый UI.

## Comments

Фикстуры должны отражать фактические поля системы, а не добавлять незапрошенные доменные решения.

## Done definition

Fixtures committed, parser/preview regression green, documented conversion rules.

## Related docs

`docs/import/Расписание_wolf_import_formatted.csv`, `docs/import/projects_import.csv`, `.scratch/release-0.21/spec.md`.

## Verification evidence

Not started.

## Answer

Not resolved.

## Status History

- `ready-for-agent`: initial ticket creation.

## Safety

No silent inference or partial import.

## End

Status remains `ready-for-agent` until implementation evidence is added.

## Dependencies

02 — Export; 03 — Preview; 04 — Apply.

## Contract

No external IDs are inferred from names at apply time.

## Final check

Run focused compatibility tests and preview the full schedule fixture.

## End of ticket

Status remains `ready-for-agent` until implementation evidence is added.

## Scope note

Only fixtures and compatibility coverage belong here; API implementation is covered by tickets 02–04.

## Reviewer focus

Dates, 15-minute alignment, repeated Delo titles, links and legacy behavior.

## Non-goals

No live synchronization.

## Exit criteria

All fixtures validate and regression tests pass.

## Appendix

The canonical workbook contract is defined in ticket 01.

## Ownership

Fixtures contain no secrets or another user's data.

## Rollback

No domain writes are made by fixture preparation.

## Related release

`.scratch/release-0.21/spec.md`.

## Done

Awaiting implementation.

## End

