/**
 * WOLF — ticket 07 regression: each sub-row of a merged multi-slot block must
 * carry its OWN real 15-minute slot (so a click on the bottom row reaches the
 * backend as the LAST slot → shrink from the END, not from the start).
 */
import assert from 'node:assert/strict'
import { buildDayBlocks } from './src/weekViewBlocks.js'

/** A covering-entry resolver: single entry 09:00–10:00, id 42. */
const entry = {
  id: 42,
  deloTitle: 'Тестовое дело',
  adHocText: '',
  status: 'PLANNED',
  startAt: '2026-08-31T09:00:00',
  endAt: '2026-08-31T10:00:00'
}

/** Visible rows 08:00–10:30 step 15 min (all day rows, 11 slots). */
function rows() {
  const out = []
  for (let idx = 0; idx < 11; idx++) {
    const h = 8 + Math.floor(idx / 4)
    const m = (idx % 4) * 15
    out.push({
      index: idx,
      label: `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`,
      absStart: null,
      isNight: false,
      minute: m,
      hour: h
    })
  }
  return out
}

const day = { date: '2026-08-31', dayStart: '2026-08-31T08:00' }
const firstDay = day

function covering(slotStart) {
  return slotStart >= entry.startAt && slotStart < entry.endAt ? entry : null
}

const cells = buildDayBlocks(day, rows(), covering, firstDay)

// Block shape: one 'block' (span 4) + three 'cont' continuations at 09:00–09:45.
assert.equal(cells[4].kind, 'block', '09:00 row should start the block')
assert.equal(cells[4].span, 4, '09:00–10:00 entry should span 4 slots')
assert.equal(cells[4].slot.startAt, '2026-08-31T09:00:00', 'block slot = entry start')

assert.equal(cells[5].kind, 'cont', '09:15 row is a continuation')
assert.equal(cells[5].slot.startAt, '2026-08-31T09:15:00', 'cont 09:15 must carry its OWN slot (regression: was block top 09:00)')

assert.equal(cells[6].kind, 'cont', '09:30 row is a continuation')
assert.equal(cells[6].slot.startAt, '2026-08-31T09:30:00', 'cont 09:30 must carry its OWN slot')

assert.equal(cells[7].kind, 'cont', '09:45 row is a continuation')
assert.equal(cells[7].slot.startAt, '2026-08-31T09:45:00', 'cont 09:45 must carry its OWN slot (regression: was 09:00)')

// The 09:45 slot equals entry.endAt − 15min → backend gridClick shrinks from the END.
assert.equal(cells[7].slot.startAt, '2026-08-31T09:45:00', 'bottom sub-slot = endAt−15 → shrink-from-end branch')
// The 09:00 slot equals entry.startAt → backend gridClick shrinks from the START.
assert.equal(cells[4].slot.startAt, '2026-08-31T09:00:00', 'top sub-slot = startAt → shrink-from-start branch')

// Extend semantics stay intact: empty 10:00 slot right below the entry.
assert.equal(cells[8].kind, 'empty', '10:00 is empty')
assert.equal(cells[8].slot.startAt, '2026-08-31T10:00:00', '10:00 slot = entry.endAt → EXTEND branch')
// Empty 08:45 slot right above the entry.
assert.equal(cells[3].kind, 'empty', '08:45 is empty')
assert.equal(cells[3].slot.startAt, '2026-08-31T08:45:00', '08:45 slot = startAt−15 → EXTEND branch')

// Surfaces for the quick-edit overlay label.
const entryLast = entry.endAt
const firstSlot = cells[4].slot.startAt
const lastSlot = cells[7].slot.startAt
assert.equal(firstSlot, entry.startAt, 'top overlay label: −15 с начала')
assert.equal(lastSlot, '2026-08-31T09:45:00', 'bottom overlay label: −15 с конца (entry end ' + entryLast + ')')

console.log('weekViewBlocks.test.mjs: OK — cont rows carry their own slots; top/bottom shrink and extend edges correct')
