# WOLF data-sync workbook 0.21

The canonical backup/restore format is an XLSX workbook with a `manifest` sheet and one sheet per supported user-owned entity.

## Contract

- `manifest` declares `format=wolf-data`, `version=0.21`, generated timestamp, and row counts.
- Every entity row starts with a stable `externalId`; database IDs are never authoritative in the workbook.
- References use `externalId`; lists use `|`; complex values use JSON text in a single cell.
- Dates use `YYYY-MM-DD`; local date-times use `YYYY-MM-DDTHH:mm`; time intervals are half-open `[startAt,endAt)`.
- Preview validates the complete workbook before apply. Preview creates no domain records.
- Apply is an authenticated, atomic upsert. Missing rows are not deleted by default.
- `deleteMissing=true` requires a non-empty explicit list of scopes. The current apply implementation rejects an empty destructive scope list and does not delete by default.
- Legacy CSV Delos and XLSX grid endpoints remain unchanged.

## Endpoints

- `GET /api/v1/data-sync/manifest?version=0.21`
- `GET /api/v1/data-sync/export?format=xlsx&version=0.21`
- `POST /api/v1/data-sync/import/preview` (`multipart/form-data`, part `file`)
- `GET /api/v1/data-sync/import/{id}/preview`
- `POST /api/v1/data-sync/import/{id}/apply` with `{ "checksum": "...", "deleteMissing": false, "scopes": [] }`
- `GET /api/v1/data-sync/import/{id}/result`

All endpoints require the authenticated WOLF user except legacy endpoints whose existing security contract is unchanged.
