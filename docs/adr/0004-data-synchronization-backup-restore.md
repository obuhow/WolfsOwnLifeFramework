# Data synchronization uses a versioned XLSX workbook with preview/apply

Status: Accepted
Date: 2026-08-21

## Context

WOLF has several user-owned entities and legacy import flows. A backup must preserve links, statuses and time history, while a normal synchronization must not delete data accidentally. The user also needs to inspect a file before writing it.

## Decision

Release 0.21 uses one versioned XLSX workbook as the canonical full backup/restore format. The workbook has a manifest and one sheet per supported entity. Every exported entity has a stable externalId; relationships use externalIds and never database ids.

Import is a two-step preview/apply operation. Preview validates the complete workbook and produces a checksum and change plan. Apply accepts only a valid preview and is atomic. The default operation is upsert: create missing externalIds, update existing ones, and skip unchanged rows. Missing rows are not deleted by default. Deletion is an explicit, scoped option shown in preview.

The format includes all supported user-owned domain data, including planned and completed time entries, but excludes secrets, service logs and binary note audio. Legacy CSV/XLSX endpoints remain compatible while the new workbook is the canonical complete backup.

## Consequences

- A complete export can be restored and round-tripped without name-based ambiguity.
- Users can safely validate large imports before changing their data.
- The import implementation needs staging/preview persistence, checksum handling and ordered relationship resolution.
- Schema changes require a new version or an explicit backward-compatible migration.
- XLSX is convenient for users but requires Apache POI limits and clear handling of dates, formulas and cell types.

## Alternatives rejected

- Immediate import: unsafe for large files and poor diagnostics.
- Name-only matching: renames and repeated titles create ambiguous merges.
- Full replacement by default: too destructive for routine synchronization.
- Live calendar OAuth sync: a separate product boundary, not file backup/restore.
