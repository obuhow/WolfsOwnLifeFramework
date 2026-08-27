<!--
  WOLF — Wolf's Own Life Framework
  Copyright (C) 2025 Pavel Obukhov

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU Affero General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  GNU Affero General Public License for more details.

  You should have received a copy of the GNU Affero General Public License
  along with this program. If not, see <https://www.gnu.org/licenses/>.
-->
<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { apiBase } from '../api'

const loading = ref(false)
const error = ref('')
const timezone = ref('Europe/Moscow')
const weekStart = ref('') // Monday YYYY-MM-DD
const weekEndExclusive = ref('')
const rangeStart = ref('')
const rangeEnd = ref('')
const isoYear = ref(null)
const isoWeek = ref(null)
const days = ref([]) // [{ date, weekday }]
const entries = ref([])
const delos = ref([])
const projects = ref([])
const nightStart = ref('23:00')
const nightEnd = ref('07:00')
const dayEndSetting = ref('02:00')
const showNightHours = ref(false)

// Weekly project backlog (ticket 04 — same grouped register as Today)
const weekBacklog = ref([])
const projectWeekHours = ref({})
const backlogLoading = ref(false)
const backlogError = ref('')
const executionModeFilter = ref('ALL')
const EXECUTION_MODES = [
  { value: 'ALL', label: 'Все' },
  { value: 'SELF', label: 'Мне' },
  { value: 'DELEGATABLE', label: 'Делегировать' },
  { value: 'AUTOMATABLE', label: 'Автоматизировать' }
]

// Picker
const pickerOpen = ref(false)
const pickerSlot = ref(null)
const pickerMode = ref('delo') // 'delo' | 'adhoc' | 'create'
const pickerDeloId = ref('')
const pickerAdHoc = ref('')
const pickerFilter = ref('')
const quickTitle = ref('')
const quickCreating = ref(false)
const saving = ref(false)

const SLOTS_PER_DAY = 96
const SHOW_NIGHT_KEY = 'wolf_show_night_hours'

const WEEKDAY_RU = {
  MONDAY: 'Пн',
  TUESDAY: 'Вт',
  WEDNESDAY: 'Ср',
  THURSDAY: 'Чт',
  FRIDAY: 'Пт',
  SATURDAY: 'Сб',
  SUNDAY: 'Вс'
}

function authHeaders(json = false) {
  const token = localStorage.getItem('wolf_token')
  if (!token) {
    window.location.href = '#/login'
    return null
  }
  const headers = { Authorization: `Bearer ${token}` }
  if (json) headers['Content-Type'] = 'application/json'
  return headers
}

function pad2(n) {
  return String(n).padStart(2, '0')
}

function formatTime(hour, minute) {
  return `${pad2(hour)}:${pad2(minute)}`
}

function slotStartAt(dateStr, index) {
  const totalMin = index * 15
  const h = Math.floor(totalMin / 60)
  const m = totalMin % 60
  return `${dateStr}T${formatTime(h, m)}:00`
}

function parseSlotLabel(startAt) {
  const t = startAt.includes('T') ? startAt.split('T')[1] : startAt
  return t.slice(0, 5)
}

function parseSlotDate(startAt) {
  return startAt.includes('T') ? startAt.split('T')[0] : startAt.slice(0, 10)
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
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}T${formatTime(d.getHours(), d.getMinutes())}:00`
}

function addMinutes(ldtStr, mins) {
  const d = parseLdt(ldtStr)
  d.setMinutes(d.getMinutes() + mins)
  return formatLdt(d)
}

function entryCovering(slotStart) {
  const s = normalizeStart(slotStart)
  for (const e of entries.value) {
    const a = normalizeStart(e.startAt)
    const b = normalizeStart(e.endAt || addMinutes(a, 15))
    if (a <= s && s < b) return e
  }
  return null
}


function timeToMinutes(t) {
  if (!t) return 0
  const [h, m] = t.slice(0, 5).split(':').map(Number)
  return h * 60 + m
}

function isNightSlotLabel(labelHHMM) {
  const start = timeToMinutes(nightStart.value)
  const end = timeToMinutes(nightEnd.value)
  const slot = timeToMinutes(labelHHMM)
  if (start === end) return false
  if (start < end) return slot >= start && slot < end
  return slot >= start || slot < end
}

function addDays(dateStr, delta) {
  const [y, m, d] = dateStr.split('-').map(Number)
  const dt = new Date(y, m - 1, d)
  dt.setDate(dt.getDate() + delta)
  return `${dt.getFullYear()}-${pad2(dt.getMonth() + 1)}-${pad2(dt.getDate())}`
}

function formatDayHeader(dateStr) {
  const [, m, d] = dateStr.split('-')
  return `${Number(d)}.${m}`
}

const entryByStart = computed(() => {
  const map = new Map()
  for (const e of entries.value) {
    map.set(normalizeStart(e.startAt), e)
  }
  return map
})

const timeRows = computed(() => {
  // Prefer first day bounds from week payload
  const d0 = days.value[0]
  let start = d0?.dayStart ? normalizeStart(d0.dayStart) : (rangeStart.value ? normalizeStart(rangeStart.value) : null)
  let end = d0?.dayEnd ? normalizeStart(d0.dayEnd) : (rangeStart.value ? addMinutes(normalizeStart(rangeStart.value), 24 * 60) : null)
  if (!start || !end) {
    const rows = []
    for (let i = 0; i < SLOTS_PER_DAY; i++) {
      const totalMin = i * 15
      const label = formatTime(Math.floor(totalMin / 60), totalMin % 60)
      rows.push({ index: i, label, hour: Math.floor(totalMin / 60), minute: totalMin % 60, isNight: isNightSlotLabel(label), absStart: null })
    }
    return rows
  }
  const rows = []
  let cur = normalizeStart(start)
  const endN = normalizeStart(end)
  let i = 0
  while (cur < endN && i < 200) {
    const label = parseSlotLabel(cur)
    rows.push({
      index: i,
      label,
      hour: Number(label.slice(0, 2)),
      minute: Number(label.slice(3, 5)),
      isNight: isNightSlotLabel(label),
      absStart: cur
    })
    cur = addMinutes(cur, 15)
    i++
  }
  return rows
})

const visibleTimeRows = computed(() => {
  if (showNightHours.value) return timeRows.value
  return timeRows.value.filter(r => !r.isNight)
})


function cellFor(dayDate, row) {
  const startAt = slotStartAt(dayDate, row.index)
  const entry = entryByStart.value.get(normalizeStart(startAt)) || null
  return {
    startAt,
    label: row.label,
    hour: row.hour,
    minute: row.minute,
    isNight: row.isNight,
    entry,
    date: dayDate
  }
}

function isPastSlot(startAt) {
  const now = new Date()
  const slot = new Date(startAt)
  return slot.getTime() <= now.getTime()
}

const nowSlotStart = computed(() => {
  const d = new Date()
  const dateStr = `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
  const mins = d.getHours() * 60 + d.getMinutes()
  const aligned = Math.floor(mins / 15) * 15
  const h = Math.floor(aligned / 60)
  const m = aligned % 60
  return `${dateStr}T${formatTime(h, m)}:00`
})

const delosByProject = computed(() => {
  const q = pickerFilter.value.trim().toLowerCase()
  const filtered = delos.value.filter(d => {
    if (!q) return true
    return (d.title || '').toLowerCase().includes(q)
  })

  const groups = new Map()
  const projectTitle = id => projects.value.find(p => p.id === id)?.title || `Проект #${id}`

  for (const d of filtered) {
    const pids = d.projectIds && d.projectIds.length ? d.projectIds : [null]
    for (const pid of pids) {
      const key = pid == null ? '__none__' : String(pid)
      const label = pid == null ? 'Без проекта' : projectTitle(pid)
      if (!groups.has(key)) groups.set(key, { key, label, items: [] })
      const g = groups.get(key)
      if (!g.items.some(x => x.id === d.id)) g.items.push(d)
    }
  }

  return Array.from(groups.values()).sort((a, b) => {
    if (a.key === '__none__') return 1
    if (b.key === '__none__') return -1
    return a.label.localeCompare(b.label, 'ru')
  })
})

const panelItems = computed(() => {
  return entries.value
    .slice()
    .sort((a, b) => a.startAt.localeCompare(b.startAt))
    .map(e => ({
      ...e,
      title: e.deloTitle || e.adHocText || '—',
      time: `${parseSlotLabel(e.startAt)}–${parseSlotLabel(e.endAt)}`,
      day: parseSlotDate(e.startAt),
      statusLabel: e.status === 'DONE' ? 'выполнена' : 'запланирована'
    }))
})

const pendingConfirmCount = computed(() => {
  return entries.value.filter(
    e => e.status === 'PLANNED' && isPastSlot(normalizeStart(e.startAt))
  ).length
})

const weekLabel = computed(() => {
  if (!isoYear.value || !isoWeek.value) return ''
  return `${isoYear.value}-W${String(isoWeek.value).padStart(2, '0')}`
})

const nightHoursLabel = computed(() => `${nightStart.value}–${nightEnd.value}`)

function formatHours(value) {
  const n = Number(value || 0)
  return Number.isInteger(n) ? String(n) : n.toFixed(2).replace(/\.?0+$/, '')
}
function hoursOrDash(value) {
  if (value == null) return '—'
  return formatHours(value)
}

const filteredWeekBacklog = computed(() => {
  if (executionModeFilter.value === 'ALL') return weekBacklog.value
  return weekBacklog.value.filter(d => d.executionMode === executionModeFilter.value)
})

/** Weekly backlog grouped by Project with real x / y ч (see ticket 03). */
const backlogGroups = computed(() => {
  const projectTitle = id => projects.value.find(p => p.id === id)?.title || `Проект #${id}`
  const groups = new Map()
  for (const delo of filteredWeekBacklog.value) {
    const pids = (delo.projectIds && delo.projectIds.length) ? delo.projectIds : [null]
    for (const pid of pids) {
      const key = pid == null ? '__none__' : String(pid)
      if (!groups.has(key)) {
        const hours = pid == null ? null : projectWeekHours.value[String(pid)]
        groups.set(key, {
          key,
          projectId: pid,
          label: pid == null ? 'Без проекта' : projectTitle(pid),
          fact: pid == null ? null : (hours ? hours.fact : 0),
          plan: pid == null ? null : (hours ? hours.plan : null),
          items: []
        })
      }
      const g = groups.get(key)
      if (!g.items.some(x => x.id === delo.id)) g.items.push(delo)
    }
  }
  return Array.from(groups.values()).sort((a, b) => {
    if (a.key === '__none__') return 1
    if (b.key === '__none__') return -1
    return a.label.localeCompare(b.label, 'ru')
  })
})

function groupHoursLabel(group) {
  if (group.projectId == null) return ''
  return `${hoursOrDash(group.fact ?? 0)} / ${hoursOrDash(group.plan)} ч`
}
function executionModeLabel(mode) {
  const found = EXECUTION_MODES.find(m => m.value === mode)
  return found ? found.label : (mode || '')
}

/** Load the grouped weekly backlog for the current ISO week + real project hours. */
async function loadWeekBacklog() {
  if (isoYear.value == null || isoWeek.value == null) return
  backlogLoading.value = true
  backlogError.value = ''
  try {
    const headers = authHeaders()
    if (!headers) return
    const res = await fetch(`${apiBase()}/backlog/week/${isoYear.value}/${isoWeek.value}`, { headers })
    if (!res.ok) throw new Error(`Бэклог недели: HTTP ${res.status}`)
    const body = await res.json()
    const byId = new Map(delos.value.map(d => [d.id, d]))
    weekBacklog.value = (body.delos || []).map(d => ({
      ...d,
      projectIds: byId.get(d.id)?.projectIds || []
    }))
    await loadProjectWeekHours()
  } catch (e) {
    backlogError.value = e instanceof Error ? e.message : String(e)
  } finally {
    backlogLoading.value = false
  }
}

/** Real current-week plan/fact hours per Project from the Gantt aggregate. */
async function loadProjectWeekHours() {
  const headers = authHeaders()
  if (!headers || !weekStart.value) return
  try {
    const res = await fetch(`${apiBase()}/gantt?from=${weekStart.value}&weeks=1`, { headers })
    if (!res.ok) { projectWeekHours.value = {}; return }
    const body = await res.json()
    const map = {}
    for (const row of body.projects || []) {
      const cell = (row.cells || [])[0] || {}
      map[String(row.id)] = {
        plan: cell.planHours == null ? null : Number(cell.planHours),
        fact: Number(cell.factHours || 0)
      }
    }
    projectWeekHours.value = map
  } catch (e) {
    projectWeekHours.value = {}
  }
}

async function removeFromBacklog(deloId) {
  if (isoYear.value == null || isoWeek.value == null) return
  const headers = authHeaders()
  if (!headers) return
  saving.value = true
  error.value = ''
  try {
    const res = await fetch(`${apiBase()}/backlog/week/${isoYear.value}/${isoWeek.value}/delos/${deloId}`, {
      method: 'DELETE',
      headers
    })
    if (!res.ok) throw new Error(`Удаление из бэклога: HTTP ${res.status}`)
    await loadWeekBacklog()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    saving.value = false
  }
}

/** Identity of a slot content for merge: same delo OR same ad-hoc text + same status. */
function entryMergeKey(entry) {
  if (!entry) return null
  if (entry.deloId != null) return `d:${entry.deloId}:${entry.status}`
  if (entry.adHocText) return `a:${entry.adHocText}:${entry.status}`
  return null
}

/**
 * Per day column: for each visible row either a block-start cell (with rowspan)
 * or a continuation (skipped in template) or an empty single cell.
 * Contiguous same-name entries render as one block: "Сон 00:00–07:00".
 */
const dayBlocks = computed(() => {
  const rows = visibleTimeRows.value
  if (!days.value.length) return []
  return days.value.map(day => {
    const cells = []
    let i = 0
    while (i < rows.length) {
      const row = rows[i]
      // Map row clock onto this day's absolute timeline
      let slotStart
      if (row.absStart && days.value[0]) {
        // same offset from day start as row.absStart from first day start
        const d0 = days.value[0]
        const base0 = d0.dayStart ? normalizeStart(d0.dayStart) : normalizeStart(row.absStart)
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
      slotStart = normalizeStart(slotStart)
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
      let j = i + 1
      while (j < rows.length) {
        const r2 = rows[j]
        let s2
        if (r2.absStart && days.value[0]) {
          const d0 = days.value[0]
          const base0 = d0.dayStart ? normalizeStart(d0.dayStart) : normalizeStart(r2.absStart)
          const offsetMs = parseLdt(r2.absStart) - parseLdt(base0)
          const dayBase = day.dayStart ? normalizeStart(day.dayStart) : `${day.date}T${r2.label}:00`
          const dt = parseLdt(dayBase)
          dt.setTime(dt.getTime() + offsetMs)
          s2 = formatLdt(dt)
        } else if (day.dayStart) {
          s2 = addMinutes(normalizeStart(day.dayStart), r2.index * 15)
        } else {
          s2 = `${day.date}T${r2.label}:00`
        }
        const e2 = entryCovering(normalizeStart(s2))
        if (!e2 || e2.id !== entry.id) break
        j += 1
      }
      const span = j - i
      const startLabel = parseSlotLabel(entry.startAt)
      const endLabel = parseSlotLabel(entry.endAt)
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
        cells.push({
          kind: 'cont',
          rowIndex: k,
          span: 0,
          slot: { startAt: slotStart, label: rows[k].label, entry, date: day.date, isNight: rows[k].isNight, minute: rows[k].minute, hour: rows[k].hour },
          displayLabel: '',
          rangeLabel: ''
        })
      }
      i = j
    }
    return cells
  })
})

function blockAt(dayIdx, rowIdx) {
  const col = dayBlocks.value[dayIdx]
  if (!col) return null
  return col[rowIdx] || null
}

function blockClass(block) {
  if (!block) return ['week-cell']
  const classes = cellClass(block.slot)
  if (block.kind === 'block' && block.span > 1) classes.push('cell-span')
  return classes
}

function blockTitle(block) {
  if (!block || block.kind === 'cont') return ''
  if (block.kind === 'empty' || !block.slot.entry) {
    return `${block.slot.date} ${block.slot.label} — пусто, нажмите чтобы поставить`
  }
  const name = block.slot.entry.deloTitle || block.slot.entry.adHocText
  const range = block.span > 1 ? ` ${block.rangeLabel}` : ''
  if (block.slot.entry.status === 'PLANNED' && isPastSlot(block.slot.startAt)) {
    return `${name}${range} (запланирована, прошло) — подтвердить`
  }
  const st = block.slot.entry.status === 'DONE' ? 'выполнена' : 'запланирована'
  return `${name}${range} (${st}) — снять`
}


async function loadProjects() {
  const headers = authHeaders()
  if (!headers) return
  const res = await fetch(`${apiBase()}/projects`, { headers })
  if (!res.ok) throw new Error(`Проекты: HTTP ${res.status}`)
  projects.value = await res.json()
}

async function loadDelos() {
  const headers = authHeaders()
  if (!headers) return
  const res = await fetch(`${apiBase()}/delos`, { headers })
  if (!res.ok) throw new Error(`Дела: HTTP ${res.status}`)
  delos.value = await res.json()
}

async function loadSettings() {
  const headers = authHeaders()
  if (!headers) return
  const res = await fetch(`${apiBase()}/settings`, { headers })
  if (!res.ok) throw new Error(`Настройки: HTTP ${res.status}`)
  const data = await res.json()
  timezone.value = data.timezone || timezone.value
  nightStart.value = (data.nightStart || '23:00:00').slice(0, 5)
  nightEnd.value = (data.nightEnd || '07:00:00').slice(0, 5)
  dayEndSetting.value = (data.dayEnd || '02:00:00').slice(0, 5)
}

function applyWeekBody(body) {
  timezone.value = body.timezone || timezone.value
  weekStart.value = body.weekStart
  weekEndExclusive.value = body.weekEndExclusive
  rangeStart.value = body.rangeStart
  rangeEnd.value = body.rangeEnd
  isoYear.value = body.isoYear
  isoWeek.value = body.isoWeek
  days.value = body.days || []
  entries.value = body.entries || []
  if (body.dayEndSetting) dayEndSetting.value = String(body.dayEndSetting).slice(0, 5)
}

async function ensureSleepForWeek() {
  if (!rangeStart.value || !rangeEnd.value) return
  const headers = authHeaders(true)
  if (!headers) return
  const res = await fetch(`${apiBase()}/time-entries/ensure-sleep`, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      from: normalizeStart(rangeStart.value),
      to: normalizeStart(rangeEnd.value)
    })
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    throw new Error(err.message || `Авто-Сон: HTTP ${res.status}`)
  }
}

/**
 * @param {{ date?: string, isoYear?: number, isoWeek?: number, ensureSleep?: boolean } | null} query
 * ensureSleep (default true) — only on navigation/first open. After place/clear/confirm
 * we skip it so manually cleared night «Сон» is not immediately refilled.
 */
async function loadWeek(query = null) {
  const headers = authHeaders()
  if (!headers) return
  const doEnsureSleep = query == null ? true : query.ensureSleep !== false

  const params = new URLSearchParams()
  if (query?.isoYear != null && query?.isoWeek != null) {
    params.set('isoYear', String(query.isoYear))
    params.set('isoWeek', String(query.isoWeek))
  } else if (query?.date) {
    params.set('date', query.date)
  } else if (weekStart.value) {
    params.set('date', weekStart.value)
  }

  const qs = params.toString() ? `?${params}` : ''
  let res = await fetch(`${apiBase()}/time-entries/week${qs}`, { headers })
  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    throw new Error(err.message || `Неделя: HTTP ${res.status}`)
  }
  let body = await res.json()
  applyWeekBody(body)

  if (doEnsureSleep) {
    await ensureSleepForWeek()
    // reload after sleep autofill
    const reloadParams = new URLSearchParams({
      isoYear: String(isoYear.value),
      isoWeek: String(isoWeek.value)
    })
    res = await fetch(`${apiBase()}/time-entries/week?${reloadParams}`, { headers })
    if (!res.ok) throw new Error(`Неделя: HTTP ${res.status}`)
    body = await res.json()
    applyWeekBody(body)
  }
  await loadWeekBacklog()
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const stored = localStorage.getItem(SHOW_NIGHT_KEY)
    if (stored === '1') showNightHours.value = true
    if (stored === '0') showNightHours.value = false
    await Promise.all([loadProjects(), loadDelos(), loadSettings()])
    await loadWeek(null)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

function toggleNightHours() {
  showNightHours.value = !showNightHours.value
  localStorage.setItem(SHOW_NIGHT_KEY, showNightHours.value ? '1' : '0')
}

async function shiftWeek(deltaWeeks) {
  if (!weekStart.value) return
  const anchor = addDays(weekStart.value, deltaWeeks * 7)
  loading.value = true
  error.value = ''
  try {
    await loadWeek({ date: anchor })
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function goThisWeek() {
  loading.value = true
  error.value = ''
  try {
    weekStart.value = ''
    await loadWeek(null)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function onWeekDatePick(ev) {
  const val = ev.target?.value
  if (!val) return
  loading.value = true
  error.value = ''
  try {
    await loadWeek({ date: val })
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function onCellClick(slot, span = 1) {
  const headers = authHeaders(true)
  if (!headers) return
  saving.value = true
  error.value = ''
  try {
    const res = await fetch(`${apiBase()}/time-entries/grid-click`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ slotStart: normalizeStart(slot.startAt) })
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      throw new Error(err.message || `Клик: HTTP ${res.status}`)
    }
    const body = await res.json()
    if (body.action === 'NEED_PICKER') {
      openPicker(slot.startAt)
      return
    }
    await loadWeek({ isoYear: isoYear.value, isoWeek: isoWeek.value, ensureSleep: false })
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    saving.value = false
  }
}

function openPicker(startAt) {
  pickerSlot.value = startAt
  pickerMode.value = 'delo'
  pickerDeloId.value = ''
  pickerAdHoc.value = ''
  pickerFilter.value = ''
  quickTitle.value = ''
  pickerOpen.value = true
}

function closePicker() {
  pickerOpen.value = false
  pickerSlot.value = null
}

async function clearSlot(startAt) {
  const headers = authHeaders()
  if (!headers) return
  saving.value = true
  error.value = ''
  try {
    const qs = `?startAt=${encodeURIComponent(startAt)}`
    const res = await fetch(`${apiBase()}/time-entries${qs}`, {
      method: 'DELETE',
      headers
    })
    if (!res.ok && res.status !== 204) throw new Error(`Очистка: HTTP ${res.status}`)
    await loadWeek({ isoYear: isoYear.value, isoWeek: isoWeek.value, ensureSleep: false })
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    saving.value = false
  }
}

async function confirmSlot(startAt) {
  const headers = authHeaders(true)
  if (!headers) return
  saving.value = true
  error.value = ''
  try {
    const res = await fetch(`${apiBase()}/time-entries/confirm`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ startAt: normalizeStart(startAt) })
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      throw new Error(err.message || `Подтверждение: HTTP ${res.status}`)
    }
    await loadWeek({ isoYear: isoYear.value, isoWeek: isoWeek.value, ensureSleep: false })
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    saving.value = false
  }
}

async function confirmAllWeek() {
  if (!rangeStart.value || !rangeEnd.value) return
  const headers = authHeaders(true)
  if (!headers) return
  saving.value = true
  error.value = ''
  try {
    const res = await fetch(`${apiBase()}/time-entries/confirm-all`, {
      method: 'POST',
      headers,
      body: JSON.stringify({
        from: normalizeStart(rangeStart.value),
        to: normalizeStart(rangeEnd.value)
      })
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      throw new Error(err.message || `Подтвердить все: HTTP ${res.status}`)
    }
    await loadWeek({ isoYear: isoYear.value, isoWeek: isoWeek.value, ensureSleep: false })
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    saving.value = false
  }
}

async function submitPicker() {
  if (!pickerSlot.value) return
  const headers = authHeaders(true)
  if (!headers) return

  let body
  if (pickerMode.value === 'delo') {
    if (!pickerDeloId.value) {
      error.value = 'Выберите Дело'
      return
    }
    body = { startAt: pickerSlot.value, deloId: Number(pickerDeloId.value) }
  } else if (pickerMode.value === 'adhoc') {
    const text = pickerAdHoc.value.trim()
    if (!text) {
      error.value = 'Введите текст'
      return
    }
    body = { startAt: pickerSlot.value, adHocText: text }
  } else {
    return
  }

  saving.value = true
  error.value = ''
  try {
    const payload = { slotStart: normalizeStart(pickerSlot.value) }
    if (body.deloId != null) payload.deloId = body.deloId
    if (body.adHocText) payload.adHocText = body.adHocText
    const res = await fetch(`${apiBase()}/time-entries/grid-click`, {
      method: 'POST',
      headers,
      body: JSON.stringify(payload)
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      throw new Error(err.message || `Сохранение: HTTP ${res.status}`)
    }
    closePicker()
    await loadWeek({ isoYear: isoYear.value, isoWeek: isoWeek.value, ensureSleep: false })
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    saving.value = false
  }
}

/** Quick-create Дело from week picker, then select it. */
async function quickCreateDelo() {
  const title = quickTitle.value.trim()
  if (!title) {
    error.value = 'Введите название Дела'
    return
  }
  const headers = authHeaders(true)
  if (!headers) return
  quickCreating.value = true
  error.value = ''
  try {
    const res = await fetch(`${apiBase()}/delos`, {
      method: 'POST',
      headers,
      body: JSON.stringify({
        title,
        executionMode: 'SELF'
      })
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      throw new Error(err.message || `Создание Дела: HTTP ${res.status}`)
    }
    const created = await res.json()
    await loadDelos()
    pickerDeloId.value = String(created.id)
    pickerMode.value = 'delo'
    quickTitle.value = ''
    pickerFilter.value = created.title || ''
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    quickCreating.value = false
  }
}

function cellClass(slot) {
  const classes = ['week-cell']
  if (slot.entry) {
    classes.push(slot.entry.status === 'DONE' ? 'cell-done' : 'cell-planned')
  } else {
    classes.push('cell-empty')
  }
  if (slot.isNight) classes.push('cell-night')
  if (nowSlotStart.value && normalizeStart(slot.startAt) === normalizeStart(nowSlotStart.value)) {
    classes.push('cell-now')
  }
  if (slot.minute === 0) classes.push('cell-hour')
  return classes
}

function cellTitle(slot) {
  if (!slot.entry) return `${slot.date} ${slot.label} — пусто, нажмите чтобы поставить`
  const name = slot.entry.deloTitle || slot.entry.adHocText
  if (slot.entry.status === 'PLANNED' && isPastSlot(slot.startAt)) {
    return `${name} (запланирована, прошло) — подтвердить`
  }
  const st = slot.entry.status === 'DONE' ? 'выполнена' : 'запланирована'
  return `${name} (${st}) — снять`
}

function cellLabel(slot) {
  if (!slot.entry) return ''
  return slot.entry.deloTitle || slot.entry.adHocText || ''
}

function isTodayCol(dateStr) {
  const d = new Date()
  const today = `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
  return dateStr === today
}

onMounted(loadAll)
</script>

<template>
  <div class="week-page">
    <header class="page-header week-header">
      <div>
        <h1>Неделя</h1>
        <p class="eyebrow">
          ISO {{ weekLabel }} · пн–вс · {{ timezone }} · ночь {{ nightHoursLabel }}
        </p>
      </div>
      <div class="week-nav">
        <button type="button" class="btn btn-ghost" :disabled="loading" aria-label="Предыдущая неделя" @click="shiftWeek(-1)">←</button>
        <input
          class="input week-date-input"
          type="date"
          :value="weekStart"
          :disabled="loading"
          title="Выбрать дату — откроется её ISO-неделя"
          @change="onWeekDatePick"
        />
        <button type="button" class="btn btn-ghost" :disabled="loading" aria-label="Следующая неделя" @click="shiftWeek(1)">→</button>
        <button type="button" class="btn btn-ghost" :disabled="loading" @click="goThisWeek">
          Эта неделя
        </button>
        <button
          type="button"
          class="btn btn-ghost"
          :aria-pressed="showNightHours"
          :title="showNightHours ? 'Скрыть ночные часы' : 'Показать ночные часы'"
          :disabled="loading"
          @click="toggleNightHours"
        >
          {{ showNightHours ? 'Скрыть ночь' : 'Показать ночь' }}
        </button>
        <button
          type="button"
          class="btn btn-primary"
          :disabled="loading || saving || pendingConfirmCount === 0"
          :title="pendingConfirmCount ? `Подтвердить ${pendingConfirmCount} прошедших плановых` : 'Нет прошедших плановых записей'"
          @click="confirmAllWeek"
        >
          Подтвердить все
          <template v-if="pendingConfirmCount"> ({{ pendingConfirmCount }})</template>
        </button>
      </div>
    </header>

    <div v-if="error" class="alert alert-error" style="margin-bottom: 1rem">{{ error }}</div>
    <div v-if="loading && !days.length" class="loading">Загрузка…</div>

    <div v-else class="week-layout">
      <section class="card grid-card" aria-label="Сетка недели">
        <div class="week-scroll" role="region" aria-label="Сетка недели, прокрутка по горизонтали" tabindex="0">
          <div class="week-grid" :style="{ '--day-cols': days.length || 7 }">
            <!-- header row -->
            <div class="week-corner" aria-hidden="true" :style="{ gridColumn: 1, gridRow: 1 }"></div>
            <div
              v-for="(day, dayIdx) in days"
              :key="'h-' + day.date"
              class="week-day-head"
              :class="{ 'is-today': isTodayCol(day.date) }"
              :style="{ gridColumn: dayIdx + 2, gridRow: 1 }"
            >
              <span class="wd-name">{{ WEEKDAY_RU[day.weekday] || day.weekday }}</span>
              <span class="wd-date">{{ formatDayHeader(day.date) }}</span>
            </div>

            <!-- time rows: contiguous same-name entries merge into one block -->
            <template v-for="(row, rowIdx) in visibleTimeRows" :key="'r-' + row.index">
              <div
                class="week-time"
                :class="{ 'cell-hour': row.minute === 0, 'cell-night': row.isNight }"
                :style="{ gridColumn: 1, gridRow: rowIdx + 2 }"
              >
                {{ row.label }}
              </div>
              <template v-for="(day, dayIdx) in days" :key="day.date + '-' + row.index">
                <button
                  v-if="blockAt(dayIdx, rowIdx) && blockAt(dayIdx, rowIdx).kind !== 'cont'"
                  type="button"
                  :class="blockClass(blockAt(dayIdx, rowIdx))"
                  :title="blockTitle(blockAt(dayIdx, rowIdx))"
                  :disabled="saving"
                  :style="{
                    gridColumn: dayIdx + 2,
                    gridRow: blockAt(dayIdx, rowIdx).span > 1
                      ? ((rowIdx + 2) + ' / span ' + blockAt(dayIdx, rowIdx).span)
                      : (rowIdx + 2)
                  }"
                  @click="onCellClick(blockAt(dayIdx, rowIdx).slot, blockAt(dayIdx, rowIdx).span)"
                >
                  <span v-if="blockAt(dayIdx, rowIdx).displayLabel" class="week-cell-label">
                    {{ blockAt(dayIdx, rowIdx).displayLabel }}
                  </span>
                </button>
              </template>
            </template>
          </div>
        </div>
        <p class="hint grid-legend">
          <span class="legend-swatch planned"></span> запланирована
          <span class="legend-swatch done"></span> выполнена
          · клик: поставить / снять / подтвердить прошлое плановое
          · конец дня {{ dayEndSetting }} · ночь {{ nightHoursLabel }} скрыта по умолчанию; авто «Сон» интервалом
        </p>
      </section>

      <!-- Weekly project backlog (ticket 04 — same grouped register as Today) -->
      <aside class="card panel-card backlog-panel" aria-label="Бэклог недели">
        <div class="panel-header">
          <h2>Бэклог недели</h2>
          <div class="panel-filter">
            <label for="week-backlog-mode-filter" class="visually-hidden">Фильтр по способу исполнения</label>
            <select id="week-backlog-mode-filter" class="input input-sm" v-model="executionModeFilter">
              <option v-for="mode in EXECUTION_MODES" :key="mode.value" :value="mode.value">{{ mode.label }}</option>
            </select>
          </div>
        </div>
        <p class="panel-subtitle">Неделя {{ weekLabel }}</p>
        <div v-if="backlogLoading" class="loading">Загрузка бэклога…</div>
        <div v-else-if="backlogError" class="alert alert-error">{{ backlogError }}</div>
        <div v-else-if="backlogGroups.length" class="backlog-groups">
          <section v-for="group in backlogGroups" :key="group.key" class="backlog-group">
            <header class="backlog-group-head">
              <span class="backlog-group-title">{{ group.label }}</span>
              <span v-if="group.projectId != null" class="backlog-group-hours">{{ groupHoursLabel(group) }}</span>
            </header>
            <ul class="backlog-group-list">
              <li v-for="delo in group.items" :key="delo.id + '-' + group.key" class="backlog-delo">
                <div class="backlog-delo-body">
                  <span class="backlog-delo-title">{{ delo.title }}</span>
                  <span class="backlog-delo-meta">
                    {{ executionModeLabel(delo.executionMode) }}
                    <template v-if="delo.plannedHours != null"> · {{ formatHours(delo.plannedHours) }} ч</template>
                  </span>
                </div>
                <button
                  type="button"
                  class="btn btn-ghost btn-sm"
                  title="Убрать из бэклога"
                  :disabled="saving"
                  @click="removeFromBacklog(delo.id)"
                >×</button>
              </li>
            </ul>
          </section>
        </div>
        <p v-else class="hint">Бэклог недели пуст — добавьте Дела через «Дела».</p>
      </aside>
    </div>

    <!-- Picker modal -->
    <div v-if="pickerOpen" class="modal-backdrop" @click.self="closePicker">
      <div class="modal card" role="dialog" aria-modal="true" aria-label="Поставить запись времени">
        <header class="modal-header">
          <h2>Запись времени</h2>
          <p class="hint">
            {{ pickerSlot ? `${parseSlotDate(pickerSlot)} · ${parseSlotLabel(pickerSlot)}` : '' }}
          </p>
        </header>

        <div class="picker-tabs">
          <button
            type="button"
            class="btn"
            :class="pickerMode === 'delo' ? 'btn-primary' : 'btn-ghost'"
            @click="pickerMode = 'delo'"
          >Дело</button>
          <button
            type="button"
            class="btn"
            :class="pickerMode === 'adhoc' ? 'btn-primary' : 'btn-ghost'"
            @click="pickerMode = 'adhoc'"
          >Ad-hoc</button>
          <button
            type="button"
            class="btn"
            :class="pickerMode === 'create' ? 'btn-primary' : 'btn-ghost'"
            @click="pickerMode = 'create'"
          >Новое Дело</button>
        </div>

        <div v-if="pickerMode === 'delo'" class="form-group">
          <label for="week-picker-filter">Поиск Дела</label>
          <input id="week-picker-filter" class="input" v-model="pickerFilter" placeholder="Название…" />
          <div class="delo-picker-list">
            <div v-for="group in delosByProject" :key="group.key" class="delo-group">
              <div class="delo-group-title">{{ group.label }}</div>
              <label
                v-for="d in group.items"
                :key="d.id + '-' + group.key"
                class="delo-option"
                :class="{ selected: String(pickerDeloId) === String(d.id) }"
              >
                <input type="radio" name="week-picker-delo" :value="d.id" v-model="pickerDeloId" />
                {{ d.title }}
              </label>
            </div>
            <p v-if="!delosByProject.length" class="hint">
              Нет Дел — вкладка «Новое Дело» или ad-hoc.
            </p>
          </div>
        </div>

        <div v-else-if="pickerMode === 'adhoc'" class="form-group">
          <label for="week-picker-adhoc">Текст без создания Дела</label>
          <input
            id="week-picker-adhoc"
            class="input"
            v-model="pickerAdHoc"
            maxlength="500"
            placeholder="Например: позвонить маме"
            @keyup.enter="submitPicker"
          />
          <p class="hint">Ad-hoc не создаёт Дело в каталоге.</p>
        </div>

        <div v-else class="form-group">
          <label for="week-quick-title">Быстрое создание Дела</label>
          <input
            id="week-quick-title"
            class="input"
            v-model="quickTitle"
            maxlength="200"
            placeholder="Название нового Дела"
            @keyup.enter="quickCreateDelo"
          />
          <p class="hint">Создаст Дело (способ: мне) и выберет его для этой ячейки. Проекты можно привязать позже.</p>
          <button
            type="button"
            class="btn btn-primary"
            style="margin-top: 0.75rem"
            :disabled="quickCreating || !quickTitle.trim()"
            @click="quickCreateDelo"
          >
            {{ quickCreating ? 'Создаю…' : 'Создать и выбрать' }}
          </button>
        </div>

        <div v-if="pickerMode !== 'create'" class="form-actions">
          <button type="button" class="btn btn-primary" :disabled="saving" @click="submitPicker">
            {{ saving ? 'Сохраняю…' : (isPastSlot(pickerSlot) ? 'Поставить как выполненную' : 'Запланировать') }}
          </button>
          <button type="button" class="btn btn-ghost" :disabled="saving" @click="closePicker">Отмена</button>
        </div>
        <div v-else class="form-actions">
          <button type="button" class="btn btn-ghost" :disabled="quickCreating" @click="closePicker">Отмена</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.week-page {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.week-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

.week-nav {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.week-date-input {
  width: auto;
  min-width: 10rem;
}

.week-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 1.25rem;
  align-items: start;
}

@media (max-width: 1000px) {
  .week-layout {
    grid-template-columns: 1fr;
  }
}

.grid-card {
  padding: 1rem 1rem 0.75rem;
}

.week-scroll {
  max-height: calc(100vh - 260px);
  overflow: auto;
  border-radius: 12px;
  border: 1px solid #e6dfd4;
  background: #fdfbf7;
}

.week-grid {
  display: grid;
  grid-template-columns: 3.25rem repeat(var(--day-cols, 7), minmax(5.5rem, 1fr));
  /* Row 1 = day headers (taller); body slots stay 1.35rem */
  grid-template-rows: 2.85rem;
  grid-auto-rows: 1.35rem;
  min-width: 48rem;
}

.week-corner {
  position: sticky;
  top: 0;
  left: 0;
  z-index: 6;
  background: #f0ebe3;
  border-bottom: 1px solid #d9d1c6;
  border-right: 1px solid #e6dfd4;
  min-height: 2.85rem;
  box-sizing: border-box;
}

.week-day-head {
  position: sticky;
  top: 0;
  z-index: 5;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.1rem;
  padding: 0.35rem 0.25rem;
  background: #f0ebe3;
  border-bottom: 1px solid #d9d1c6;
  border-right: 1px solid #e6dfd4;
  font-size: 0.78rem;
  min-height: 2.85rem;
  box-sizing: border-box;
  overflow: hidden;
}

.week-day-head.is-today {
  background: #f7f7f5;
  box-shadow: inset 0 -2px 0 var(--wolf-ink);
}

.wd-name {
  font-weight: 600;
  color: var(--wolf-ink);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  font-size: 0.72rem;
}

.wd-date {
  color: #7a7268;
  font-variant-numeric: tabular-nums;
}

.week-time {
  position: sticky;
  left: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 0.4rem;
  font-size: 0.68rem;
  font-variant-numeric: tabular-nums;
  color: #8a8278;
  background: #fdfbf7;
  border-bottom: 1px solid #efe8dc;
  border-right: 1px solid #e6dfd4;
  min-height: 1.35rem;
}

.week-time.cell-hour {
  border-top: 1px solid #d9d1c6;
  color: #5c564e;
  font-weight: 500;
}

.week-time.cell-night {
  color: #6b6488;
  background: #f6f4fb;
}

.week-cell {
  min-height: 1.35rem;
  padding: 0.05rem 0.2rem;
  border: none;
  border-bottom: 1px solid #efe8dc;
  border-right: 1px solid #efe8dc;
  background: transparent;
  text-align: left;
  font-family: inherit;
  font-size: 0.65rem;
  color: #2c2a26;
  cursor: pointer;
  overflow: hidden;
  transition: background 0.1s;
  position: relative;
  z-index: 0;
}

.week-cell:hover:not(:disabled) {
  background: #f0ebe3;
}

.week-cell:disabled {
  opacity: 0.7;
  cursor: wait;
}

.week-cell.cell-hour {
  border-top: 1px solid #e2dbd0;
}

.week-cell-label {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
  line-height: 1.2;
}

.week-cell.cell-span {
  align-self: stretch;
  display: flex;
  align-items: flex-start;
  padding-top: 0.15rem;
  /* Keep under sticky day headers (z-index 5) when scrolling */
  position: relative;
  z-index: 0;
}

.week-cell.cell-span .week-cell-label {
  white-space: normal;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.15;
  max-width: 100%;
  pointer-events: none;
}

.cell-planned {
  background: #f7f8fa;
}

.cell-planned:hover:not(:disabled) {
  background: #f2f2f2;
}

.cell-done {
  background: var(--wolf-done-surface);
}

.cell-done:hover:not(:disabled) {
  background: #e6f0e5;
}

.cell-now {
  box-shadow: inset 0 0 0 1.5px var(--wolf-ink);
}

.cell-night.cell-empty {
  background: #f8f6fc;
}

.grid-legend {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  margin: 0.75rem 0 0;
}

.legend-swatch {
  display: inline-block;
  width: 0.85rem;
  height: 0.85rem;
  border-radius: 3px;
  border: 1px solid #d9d1c6;
  margin-right: 0.15rem;
}

.legend-swatch.planned {
  background: #f7f8fa;
}

.legend-swatch.done {
  background: var(--wolf-done-surface);
}

.panel-card h2 {
  margin: 0 0 0.25rem;
}

.week-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.5rem;
  max-height: calc(100vh - 300px);
  overflow-y: auto;
}

.week-list-item {
  display: grid;
  grid-template-columns: 2.8rem 1fr auto;
  gap: 0.5rem;
  align-items: start;
  padding: 0.5rem 0.6rem;
  border-radius: 10px;
  background: #f7f3ec;
  border: 1px solid #ebe3d6;
}

.week-list-actions {
  display: flex;
  gap: 0.15rem;
  align-items: center;
}

.week-list-time {
  font-variant-numeric: tabular-nums;
  font-size: 0.72rem;
  color: #7a7268;
  line-height: 1.25;
}

.week-list-body {
  display: grid;
  gap: 0.15rem;
  min-width: 0;
}

.week-list-body strong {
  font-size: 0.85rem;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
}

.week-list-meta {
  font-size: 0.72rem;
  color: #8a8278;
}

.meta-planned {
  color: var(--wolf-muted);
}

.meta-done {
  color: var(--wolf-done-ink);
}

.btn-sm {
  padding: 0.2rem 0.5rem;
  font-size: 1rem;
  line-height: 1;
}

.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(44, 42, 38, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
  padding: 1rem;
}

.modal {
  width: min(480px, 100%);
  max-height: min(85vh, 720px);
  overflow: auto;
  display: grid;
  gap: 1rem;
}

.modal-header h2 {
  margin: 0;
}

.picker-tabs {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.delo-picker-list {
  max-height: 280px;
  overflow-y: auto;
  border: 1px solid #e6dfd4;
  border-radius: 10px;
  background: #fdfbf7;
  margin-top: 0.5rem;
}

.delo-group-title {
  padding: 0.45rem 0.75rem;
  font-size: 0.7rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #8a8278;
  background: #f0ebe3;
  position: sticky;
  top: 0;
}

.delo-option {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.75rem;
  cursor: pointer;
  font-size: 0.9rem;
  border-bottom: 1px solid #efe8dc;
}

.delo-option:hover {
  background: #f7f3ec;
}

.delo-option.selected {
  background: var(--wolf-hover);
}

.delo-option input {
  accent-color: var(--wolf-ink);
}
/* Ticket 01/04: normalize weekly-grid and picker presentation; shared grid
   coordinate system (header + body tracks) is unchanged. */
.week-scroll,
.delo-picker-list { border-color: var(--wolf-rule); border-radius: 0; background: var(--wolf-surface); }
.week-cell { border-color: var(--wolf-subrule); color: var(--wolf-ink); }
.week-cell.cell-planned { background: #F7F8FA; }
.week-cell.cell-done { background: var(--wolf-done-surface); }
.week-cell:hover:not(:disabled),
.delo-option:hover,
.delo-option.selected { background: var(--wolf-hover); }
.week-cell.cell-night.cell-empty { background: var(--wolf-surface); }
.week-corner,
.week-day-head { background: var(--wolf-surface); border-color: var(--wolf-rule); }
.week-day-head.is-today { background: var(--wolf-hover); }
.week-time { background: var(--wolf-surface); border-color: var(--wolf-subrule); color: var(--wolf-muted); }
.week-time.cell-hour { border-top-color: var(--wolf-rule); color: var(--wolf-ink); }
.week-time.cell-night { background: var(--wolf-surface); color: var(--wolf-muted); }
.week-cell.cell-hour { border-top-color: var(--wolf-rule); }
.wd-date { color: var(--wolf-muted); }
.week-cell-label { color: var(--wolf-ink); }
.delo-group-title { color: var(--wolf-muted); background: var(--wolf-surface); }
.delo-option { border-bottom-color: var(--wolf-subrule); }
.legend-swatch { border-color: var(--wolf-rule); border-radius: 0; }
.modal { border-radius: 0; box-shadow: none; }

/* Weekly project backlog aside (shared with Today register) */
.week-layout { grid-template-columns: minmax(0, 1fr) 268px; }
.panel-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 6px; }
.panel-header h2 { margin: 0; }
.panel-filter { flex-shrink: 0; }
.input-sm { font-size: 12px; padding: 4px 1.5rem 4px 0; min-width: 120px; }
.visually-hidden { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; border: 0; }
.backlog-panel .panel-subtitle { margin: 0 0 12px; color: var(--wolf-muted); font-size: 11px; font-variant-numeric: tabular-nums; }
.backlog-groups { display: grid; gap: 18px; }
.backlog-group-head { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; padding-bottom: 4px; border-bottom: 1px solid var(--wolf-rule); }
.backlog-group-title { color: var(--wolf-ink); font-size: 13px; font-weight: 600; }
.backlog-group-hours { color: var(--wolf-muted); font-size: 12px; font-variant-numeric: tabular-nums; white-space: nowrap; }
.backlog-group-list { list-style: none; margin: 0; padding: 0; }
.backlog-delo { display: grid; grid-template-columns: minmax(0,1fr) auto; gap: 8px; align-items: center; padding: 8px 0; border-bottom: 1px solid var(--wolf-subrule); }
.backlog-delo-body { display: grid; gap: 2px; min-width: 0; }
.backlog-delo-title { color: var(--wolf-ink); font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.backlog-delo-meta { color: var(--wolf-muted); font-size: 11px; }
@media (max-width: 1000px) { .week-layout { grid-template-columns: 1fr; } }
</style>
