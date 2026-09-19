# Continuous time entries are stored as intervals

Status: Accepted
Date: 2026-09-12

## Context

WOLF displays time on a 15-minute grid, but storing every occupied grid cell as a separate
row makes ordinary activities unnecessarily large in the database. It also makes imported
history difficult to read and maintain: one continuous activity can become dozens of rows.

The `TimeEntry` model already has `startAt` and `endAt` and supports interval operations
(shrink, extend, split and merge). The previous release-1.4 XLSX decision nevertheless
materialized one row per 15-minute cell.

## Decision

A `TimeEntry` row represents one continuous interval of one Delo/content and one status.
The interval is `[startAt, endAt)`: `startAt` is inclusive, `endAt` is exclusive, both
boundaries are aligned to the 15-minute grid, and `endAt` must be later than `startAt`.

During XLSX materialization, adjacent cells are coalesced when they have the same normalized
Delo/content and status. A gap, a blank cell, a change of activity/content, or a status change
ends the current interval and starts another row. Separate episodes of the same Delo are not
combined across a gap.

The 15-minute grid remains the UI resolution and the precision of editable boundaries; it is
not the storage unit. Preview and conflict handling operate on the resulting intervals and
must not recreate one row per cell.

## Consequences

- A continuous two-hour activity is stored as one row, not eight rows.
- Range queries and hour aggregates continue to use `startAt`/`endAt`.
- The XLSX importer needs deterministic coalescing before apply and tests for gaps, activity
  changes, normalized names, and status changes.
- Conflict detection must handle interval overlap, not only equal 15-minute starts.
- Existing legacy 15-minute rows remain valid intervals; an explicit cleanup/migration may
  coalesce adjacent compatible rows later.
- Grid editing must preserve interval semantics when shrinking, extending, splitting, or
  merging records.

## Alternatives rejected

- One row per 15-minute cell: rejected because it treats the UI grid as the persistence model.
- One row for all occurrences of a Delo across gaps: rejected because it would falsely imply
  that the Delo covered the empty periods.
- Removing 15-minute alignment: rejected because the current calendar and editing contract use
  the 15-minute grid.
