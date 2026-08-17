# WOLF Release Cancelled — Отменённые тикеты

Status: `wontfix`
Feature slug: `release-cancelled`
Tracker: local markdown (this file)

---

## Purpose

This release collects tickets that were explicitly cancelled (wontfix) during planning or implementation. They are preserved here for historical reference and to avoid re-creating them accidentally.

---

## Cancelled Tickets

### 16 — Импорт ICS → Записи времени (from release-0.1)

**Original Status:** `ready-for-agent` → **Cancelled: `wontfix`**

**Reason for cancellation:** 
- ICS import was planned as a one-shot feature for initial migration
- User decided to use xlsx import (release-0.2 ticket 11) as the primary historical data import mechanism instead
- ICS format parsing adds complexity (RRULE, timezones, recurring events) that doesn't align with the "quiet system" philosophy — the grid model works best with explicit 15-min entries
- If calendar import is needed later, it can be re-evaluated as a separate feature with proper scope

**Original scope (preserved for reference):**
- Upload ICS endpoint
- Maps into 15-min cells in user timezone
- Creates/links Дела by summary when useful
- UI entry point
- API-test import then GET time entries range

---

## Future Considerations

If calendar import becomes necessary again, consider:
- CalDAV sync (live, not one-shot) — but this is explicitly out of scope per ADR-0003
- Google/Outlook OAuth import — requires user consent flow, not aligned with privacy-first local-first approach
- Manual ICS export → xlsx conversion → existing xlsx import pipeline

---

## Related

- Release 0.1: `.scratch/release-0.1/spec.md` (original scope including ICS)
- Release 0.2: `.scratch/release-0.2/spec.md` (xlsx import as replacement, ticket 11)
- Wayfinder map: `.scratch/wayfinder-wolf/wolf-life-os-map.md` (import strategy)