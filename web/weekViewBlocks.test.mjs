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

// ─── Release 1.4, ticket 06: visual merge of adjacent legacy 15-min entries ───
// Two neighbouring legacy rows (different ids, same Delo + status) must render
// as ONE block; a status change, a Delo change or a gap must not merge.
import { entryMergeKey } from './src/weekViewBlocks.js'

function legacyRows() {
  // 09:00–10:00, four 15-min slots
  const out = []
  for (let idx = 0; idx < 4; idx++) {
    out.push({
      index: idx,
      label: `09:${String(idx * 15).padStart(2, '0')}`,
      absStart: null,
      isNight: false,
      minute: idx * 15,
      hour: 9
    })
  }
  return out
}

const legacyDay = { date: '2026-08-31', dayStart: '2026-08-31T09:00' }

function legacyEntry(id, startMin, deloId, status, adHocText = '') {
  const h = 9 + Math.floor(startMin / 60)
  const m = startMin % 60
  const start = `2026-08-31T${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:00`
  const endMin = startMin + 15
  const eh = 9 + Math.floor(endMin / 60)
  const em = endMin % 60
  const end = `2026-08-31T${String(eh).padStart(2, '0')}:${String(em).padStart(2, '0')}:00`
  return { id, deloId, adHocText, deloTitle: deloId != null ? 'Java' : '', status, startAt: start, endAt: end }
}

function coveringFor(entries) {
  return (slotStart) => {
    for (const e of entries) {
      if (e.startAt <= slotStart && slotStart < e.endAt) return e
    }
    return null
  }
}

// merge key contract
assert.equal(entryMergeKey(legacyEntry(1, 0, 5, 'DONE')), 'd:5:DONE')
assert.equal(entryMergeKey({ id: 2, deloId: null, adHocText: 'Звонок', status: 'PLANNED', startAt: '', endAt: '' }), 'a:Звонок:PLANNED')
assert.equal(entryMergeKey(null), null)

// case 1: four adjacent legacy rows, same Delo + status → ONE block span 4
{
  const es = [legacyEntry(11, 0, 5, 'DONE'), legacyEntry(12, 15, 5, 'DONE'), legacyEntry(13, 30, 5, 'DONE'), legacyEntry(14, 45, 5, 'DONE')]
  const cells = buildDayBlocks(legacyDay, legacyRows(), coveringFor(es), legacyDay)
  assert.equal(cells.length, 4, 'merged legacy: 1 block + 3 cont')
  assert.equal(cells[0].kind, 'block')
  assert.equal(cells[0].span, 4, 'four adjacent same-Delo legacy rows merge into one block')
  assert.equal(cells[0].rangeLabel, '09:00–10:00', 'merged block range spans first startAt to LAST entry endAt')
  assert.equal(cells[0].displayLabel, 'Java 09:00–10:00')
  // each cont keeps its OWN covering entry (quick-edit must not edit the wrong record)
  assert.equal(cells[1].slot.entry.id, 12, 'cont 09:15 carries its own legacy entry')
  assert.equal(cells[2].slot.entry.id, 13, 'cont 09:30 carries its own legacy entry')
  assert.equal(cells[3].slot.entry.id, 14, 'cont 09:45 carries its own legacy entry')
  assert.equal(cells[1].slot.startAt, '2026-08-31T09:15:00')
}

// case 2: status change breaks the block
{
  const es = [legacyEntry(21, 0, 5, 'DONE'), legacyEntry(22, 15, 5, 'PLANNED'), legacyEntry(23, 30, 5, 'PLANNED'), legacyEntry(24, 45, 5, 'DONE')]
  const cells = buildDayBlocks(legacyDay, legacyRows(), coveringFor(es), legacyDay)
  assert.equal(cells[0].kind, 'block'); assert.equal(cells[0].span, 1, 'DONE row alone')
  assert.equal(cells[1].kind, 'block'); assert.equal(cells[1].span, 2, 'two PLANNED rows merge')
  assert.equal(cells[3].kind, 'block'); assert.equal(cells[3].span, 1, 'trailing DONE row alone')
}

// case 3: Delo change breaks the block
{
  const es = [legacyEntry(31, 0, 5, 'DONE'), legacyEntry(32, 15, 6, 'DONE'), legacyEntry(33, 30, 6, 'DONE'), legacyEntry(34, 45, 5, 'DONE')]
  const cells = buildDayBlocks(legacyDay, legacyRows(), coveringFor(es), legacyDay)
  assert.equal(cells[0].span, 1)
  assert.equal(cells[1].span, 2)
  assert.equal(cells[3].span, 1)
}

// case 4: a gap (empty slot) breaks the block
{
  const es = [legacyEntry(41, 0, 5, 'DONE'), legacyEntry(42, 30, 5, 'DONE'), legacyEntry(43, 45, 5, 'DONE')]
  const cells = buildDayBlocks(legacyDay, legacyRows(), coveringFor(es), legacyDay)
  assert.equal(cells[0].kind, 'block'); assert.equal(cells[0].span, 1)
  assert.equal(cells[1].kind, 'empty', '09:15 is a gap')
  assert.equal(cells[2].kind, 'block'); assert.equal(cells[2].span, 2)
}

// case 5: ad-hoc text entries merge by text + status
{
  const a1 = { id: 51, deloId: null, adHocText: 'Звонок', deloTitle: '', status: 'DONE', startAt: '2026-08-31T09:00:00', endAt: '2026-08-31T09:15:00' }
  const a2 = { id: 52, deloId: null, adHocText: 'Звонок', deloTitle: '', status: 'DONE', startAt: '2026-08-31T09:15:00', endAt: '2026-08-31T09:30:00' }
  const cells = buildDayBlocks(legacyDay, legacyRows(), coveringFor([a1, a2]), legacyDay)
  assert.equal(cells[0].kind, 'block'); assert.equal(cells[0].span, 2, 'same ad-hoc text + status merge')
  assert.equal(cells[0].displayLabel, 'Звонок 09:00–09:30')
}

// case 6: one long interval entry (ticket 07 storage) still renders as one block
{
  const long = { id: 61, deloId: 9, adHocText: '', deloTitle: 'Сон', status: 'DONE', startAt: '2026-08-31T09:00:00', endAt: '2026-08-31T10:00:00' }
  const cells = buildDayBlocks(legacyDay, legacyRows(), coveringFor([long]), legacyDay)
  assert.equal(cells[0].kind, 'block'); assert.equal(cells[0].span, 4)
  assert.equal(cells[0].displayLabel, 'Сон 09:00–10:00')
  assert.equal(cells[1].slot.entry.id, 61, 'cont of a single long entry keeps that entry')
}

console.log('weekViewBlocks.test.mjs: OK — cont rows carry their own slots; top/bottom shrink and extend edges correct; legacy merge (ticket 06) OK')
