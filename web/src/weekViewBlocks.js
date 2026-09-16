/**
 * WOLF — Week grid block building, extracted from WeekView.vue (ticket 07).
 *
 * Pure module: builds the per-day cell array (block / cont / empty) with each
 * cell carrying its OWN real 15-minute slot. The "cont" continuation cells used
 * to inherit the merged block's TOP slot — which made every click inside a
 * multi-slot record shrink from the START, even when the user clicked the
 * bottom row. Keeping the per-row slot here is what lets the quick-edit
 * ("Карандаш+") mode target the exact clicked sub-slot.
 */

function pad2(n) {
  return String(n).padStart(2, '0')
}

function normalizeStart(s) {
  if (!s) return s
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(s)) return `${s}:00`
  return s
}

function parseLdt(s) {
  const n = normalizeStart(s)
  const [d, tm] = n.split('T')
  const [y, mo, da] = d.split('-').map(Number)
  const [h, mi, se] = (tm || '00:00:00').split(':').map(Number)
  return new Date(y, mo - 1, da, h, mi || 0, se || 0, 0)
}

function formatLdt(d) {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}T${pad2(d.getHours())}:${pad2(d.getMinutes())}:00`
}

function addMinutes(ldtStr, mins) {
  const d = parseLdt(ldtStr)
  d.setMinutes(d.getMinutes() + mins)
  return formatLdt(d)
}

function parseSlotLabel(startAt) {
  if (!startAt) return ''
  const n = normalizeStart(startAt)
  const tm = n.includes('T') ? n.split('T')[1] : n
  return (tm || '').slice(0, 5)
}

/**
 * Identity of a slot content for merge (ticket 06): same Delo OR same ad-hoc
 * text, plus same status. Adjacent legacy 15-min rows with one merge key
 * render as ONE block without touching the DB.
 */
export function entryMergeKey(entry) {
  if (!entry) return null
  if (entry.deloId != null) return `d:${entry.deloId}:${entry.status}`
  if (entry.adHocText) return `a:${entry.adHocText}:${entry.status}`
  return null
}

/**
 * Absolute startAt of a given visible row, mapped onto a specific day's timeline.
 * `firstDay` is days[0], used when rows carry absolute day-start offsets.
 */
export function rowSlotStart(day, row, firstDay) {
  let slotStart
  if (row.absStart && firstDay) {
    // same offset from day start as row.absStart from first day start
    const base0 = firstDay.dayStart ? normalizeStart(firstDay.dayStart) : normalizeStart(row.absStart)
    const offsetMs = parseLdt(row.absStart) - parseLdt(base0)
    const dayBase = day.dayStart ? normalizeStart(day.dayStart) : `${day.date}T${row.label}:00`
    const dt = parseLdt(dayBase)
    dt.setTime(dt.getTime() + offsetMs)
    slotStart = formatLdt(dt)
  } else if (day.dayStart) {
    slotStart = addMinutes(normalizeStart(day.dayStart), row.index * 15)
  } else {
    slotStart = `${day.date}T${row.label}:00`
  }
  return normalizeStart(slotStart)
}

/**
 * Per day column: for each visible row either a block-start cell (with rowspan)
 * or a continuation (own real slot kept for quick-edit sub-slot clicks — ticket 07)
 * or an empty single cell.
 * Contiguous same-name entries render as one block: "Сон 00:00–07:00".
 *
 * @param {object} day day descriptor ({ date, dayStart? })
 * @param {Array} rows visibleTimeRows
 * @param {(slotStart: string) => object|null} entryCovering resolves the covering entry
 * @param {object|null} firstDay days[0], for absStart offset math
 */
export function buildDayBlocks(day, rows, entryCovering, firstDay) {
  const cells = []
  let i = 0
  while (i < rows.length) {
    const row = rows[i]
    const slotStart = rowSlotStart(day, row, firstDay)
    const entry = entryCovering(slotStart)
    if (!entry) {
      cells.push({
        kind: 'empty',
        rowIndex: i,
        span: 1,
        slot: {
          startAt: slotStart,
          label: row.label,
          entry: null,
          date: day.date,
          isNight: row.isNight,
          minute: row.minute,
          hour: row.hour
        },
        displayLabel: '',
        rangeLabel: row.label
      })
      i += 1
      continue
    }
    const mergeKey = entryMergeKey(entry)
    const covered = [entry]
    let j = i + 1
    while (j < rows.length) {
      const s2 = rowSlotStart(day, rows[j], firstDay)
      const e2 = entryCovering(s2)
      if (!e2) break
      const sameRecord = e2.id != null && e2.id === entry.id
      const sameKey = mergeKey !== null && entryMergeKey(e2) === mergeKey
      if (!sameRecord && !sameKey) break
      covered.push(e2)
      j += 1
    }
    const span = j - i
    // The block spans the FIRST covered startAt to the LAST covered endAt, so a
    // visual merge of legacy 15-min rows reads as one real interval, not 15 min.
    const startLabel = parseSlotLabel(entry.startAt)
    const lastEntry = covered[covered.length - 1]
    const endAt = lastEntry.endAt || addMinutes(entry.startAt, span * 15)
    const endLabel = parseSlotLabel(endAt)
    const name = entry.deloTitle || entry.adHocText || ''
    const displayLabel = span > 1 ? `${name} ${startLabel}–${endLabel}` : name
    cells.push({
      kind: 'block',
      rowIndex: i,
      span,
      slot: {
        startAt: slotStart,
        label: row.label,
        entry,
        date: day.date,
        isNight: row.isNight,
        minute: row.minute,
        hour: row.hour
      },
      displayLabel,
      rangeLabel: `${startLabel}–${endLabel}`
    })
    for (let k = i + 1; k < j; k++) {
      const kSlotStart = rowSlotStart(day, rows[k], firstDay)
      // Each continuation keeps ITS OWN covering record: quick-edit of a sub-slot
      // must never target a neighbouring legacy row's entry.
      const kEntry = entryCovering(kSlotStart) || entry
      cells.push({
        kind: 'cont',
        rowIndex: k,
        span: 0,
        slot: { startAt: kSlotStart, label: rows[k].label, entry: kEntry, date: day.date, isNight: rows[k].isNight, minute: rows[k].minute, hour: rows[k].hour },
        displayLabel: '',
        rangeLabel: ''
      })
    }
    i = j
  }
  return cells
}
