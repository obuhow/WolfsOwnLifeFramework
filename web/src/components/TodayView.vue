<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { apiBase, authHeaders, handleAuthFailure } from '../api'
import FocusPanel from './FocusPanel.vue'

const loading = ref(false)
const error = ref('')
const timezone = ref('Europe/Moscow')
const selectedDate = ref('')
const dayStart = ref('') // logical day start LDT from API
const dayEndExclusive = ref('')
const dayEndSetting = ref('02:00')
const entries = ref([])
const delos = ref([])
const projects = ref([])
const nightStart = ref('23:00')
const nightEnd = ref('07:00')
const timeCaptureMode = ref('PARALLEL_SLOTS')
// Default hidden per glossary / US-17
const showNightHours = ref(false)

// Week backlog state
const weekBacklog = ref([])
const backlogLoading = ref(false)
const backlogError = ref('')
// Execution mode filter for panels: 'SELF' | 'DELEGATABLE' | 'AUTOMATABLE' | 'ALL'
const executionModeFilter = ref('ALL')
const EXECUTION_MODES = [
  { value: 'ALL', label: 'Все' },
  { value: 'SELF', label: 'Мне' },
  { value: 'DELEGATABLE', label: 'Делегировать' },
  { value: 'AUTOMATABLE', label: 'Автоматизировать' }
]

// Picker state
const pickerOpen = ref(false)
const pickerSlot = ref(null) // LocalDateTime string
const pickerMode = ref('delo') // 'delo' | 'adhoc'
const pickerDeloId = ref('')
const pickerAdHoc = ref('')
const pickerFilter = ref('')
const saving = ref(false)

const SLOTS_PER_DAY = 96 // 00:00 .. 23:45
const SHOW_NIGHT_KEY = 'wolf_show_night_hours'

function pad2(n) {
  return String(n).padStart(2, '0')
}

function formatTime(hour, minute) {
  return `${pad2(hour)}:${pad2(minute)}`
}

function parseSlotLabel(startAt) {
  const t = startAt.includes('T') ? startAt.split('T')[1] : startAt
  return t.slice(0, 5)
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

/** Parse "HH:mm" or "HH:mm:ss" → minutes from midnight. */
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

function entryCovering(slotStart) {
  const s = normalizeStart(slotStart)
  for (const e of entries.value) {
    const a = normalizeStart(e.startAt)
    const b = normalizeStart(e.endAt)
    if (a <= s && s < b) return e
  }
  return null
}

/** 15-min slots spanning logical day [dayStart, dayEndExclusive). */
const slots = computed(() => {
  if (!dayStart.value || !dayEndExclusive.value) return []
  const list = []
  let cur = normalizeStart(dayStart.value)
  const end = normalizeStart(dayEndExclusive.value)
  let i = 0
  while (cur < end && i < 200) {
    const entry = entryCovering(cur)
    const label = parseSlotLabel(cur)
    list.push({
      index: i,
      startAt: cur,
      label,
      hour: Number(label.slice(0, 2)),
      minute: Number(label.slice(3, 5)),
      covering: entry,
      isNight: isNightSlotLabel(label)
    })
    cur = addMinutes(cur, 15)
    i++
  }
  return list
})

/**
 * Night filter + interval blocks recomputed on *visible* rows.
 * (If Сон starts at 02:00 and night is hidden until 08:30, first visible cell
 * must still be a block start for the remaining tail — same as WeekView.)
 */
const visibleSlots = computed(() => {
  let base = slots.value
  if (!showNightHours.value) base = base.filter(s => !s.isNight)

  const out = []
  let i = 0
  while (i < base.length) {
    const s = base[i]
    const entry = s.covering || null
    if (!entry) {
      out.push({
        ...s,
        entry: null,
        isBlockStart: false,
        isContinuation: false,
        span: 1,
        displayLabel: ''
      })
      i += 1
      continue
    }
    let j = i + 1
    while (j < base.length) {
      const e2 = base[j].covering
      if (!e2 || e2.id !== entry.id) break
      j += 1
    }
    const span = j - i
    const name = entry.deloTitle || entry.adHocText || ''
    const startLab = parseSlotLabel(entry.startAt)
    const endLab = parseSlotLabel(entry.endAt)
    // Always show full interval range when block spans or true start is off-screen (night)
    const trueStartHidden = normalizeStart(entry.startAt) !== normalizeStart(s.startAt)
    const displayLabel = span > 1 || trueStartHidden
      ? `${name} ${startLab}–${endLab}`
      : name
    out.push({
      ...s,
      entry,
      covering: entry,
      isBlockStart: true,
      isContinuation: false,
      span,
      displayLabel
    })
    // skip same-entry tail cells (rendered via min-height span)
    i = j
  }
  return out
})

function normalizeStart(s) {
  // unify "…T10:00" and "…T10:00:00"
  if (!s) return s
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(s)) return `${s}:00`
  return s
}

const nowSlotStart = computed(() => {
  // Approximate "now" in browser local time — server uses user TZ; for highlight only.
  // Better: derive from selected day + clock. We use browser clock; timezone mismatch is rare in 0.1.
  const d = new Date()
  const y = d.getFullYear()
  const mo = pad2(d.getMonth() + 1)
  const da = pad2(d.getDate())
  const dateStr = `${y}-${mo}-${da}`
  if (dateStr !== selectedDate.value) return null
  const mins = d.getHours() * 60 + d.getMinutes()
  const aligned = Math.floor(mins / 15) * 15
  const h = Math.floor(aligned / 60)
  const m = aligned % 60
  return `${dateStr}T${formatTime(h, m)}:00`
})

function isPastSlot(startAt) {
  // Compare wall-clock strings on selected day vs browser now (good enough for UI status default).
  // Server still decides actual status on create.
  const now = new Date()
  const slot = new Date(startAt)
  return slot.getTime() <= now.getTime()
}

const panelItems = computed(() => {
  return entries.value
    .slice()
    .sort((a, b) => a.startAt.localeCompare(b.startAt))
    .map(e => ({
      ...e,
      title: e.deloTitle || e.adHocText || '—',
      time: `${parseSlotLabel(e.startAt)}–${parseSlotLabel(e.endAt)}`,
      statusLabel: e.status === 'DONE' ? 'выполнена' : 'запланирована',
      executionMode: e.deloExecutionMode || 'SELF'
    }))
    .filter(e => {
      if (executionModeFilter.value === 'ALL') return true
      return e.executionMode === executionModeFilter.value
    })
})

const delosByProject = computed(() => {
  const q = pickerFilter.value.trim().toLowerCase()
  const filtered = delos.value.filter(d => {
    if (!q) return true
    return (d.title || '').toLowerCase().includes(q)
  })

  // Group: project title → delos; unlinked under «Без проекта»
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

const filteredWeekBacklog = computed(() => {
  if (executionModeFilter.value === 'ALL') return weekBacklog.value
  return weekBacklog.value.filter(d => d.executionMode === executionModeFilter.value)
})

async function loadProjects() {
  const headers = authHeaders()
  if (!headers) return
  const res = await fetch(`${apiBase()}/projects`, { headers })
  if (handleAuthFailure(res)) return
  if (!res.ok) throw new Error(`Проекты: HTTP ${res.status}`)
  projects.value = await res.json()
}

async function loadDelos() {
  const headers = authHeaders()
  if (!headers) return
  const res = await fetch(`${apiBase()}/delos`, { headers })
  if (handleAuthFailure(res)) return
  if (!res.ok) throw new Error(`Дела: HTTP ${res.status}`)
  delos.value = await res.json()
}

async function loadSettings() {
  const headers = authHeaders()
  if (!headers) return
  const res = await fetch(`${apiBase()}/settings`, { headers })
  if (handleAuthFailure(res)) return
  if (!res.ok) throw new Error(`Настройки: HTTP ${res.status}`)
  const data = await res.json()
  timezone.value = data.timezone || timezone.value
  nightStart.value = (data.nightStart || '23:00:00').slice(0, 5)
  nightEnd.value = (data.nightEnd || '07:00:00').slice(0, 5)
  dayEndSetting.value = (data.dayEnd || '02:00:00').slice(0, 5)
  timeCaptureMode.value = data.timeCaptureMode || 'PARALLEL_SLOTS'
}

function dayBounds(dateStr) {
  // Prefer server bounds when already loaded for this date
  if (selectedDate.value === dateStr && dayStart.value && dayEndExclusive.value) {
    return { from: normalizeStart(dayStart.value), to: normalizeStart(dayEndExclusive.value) }
  }
  const [y, m, d] = dateStr.split('-').map(Number)
  const [eh, em] = (dayEndSetting.value || '02:00').split(':').map(Number)
  const start = new Date(y, m - 1, d, eh, em, 0, 0)
  const end = new Date(y, m - 1, d + 1, eh, em, 0, 0)
  return { from: formatLdt(start), to: formatLdt(end) }
}


/** Autofill empty night cells with Дело «Сон» for the viewed day. Idempotent; manual wins. */
async function ensureSleepForDay() {
  if (!selectedDate.value) return
  const headers = authHeaders(true)
  if (!headers) return
  const { from, to } = dayBounds(selectedDate.value)
  const res = await fetch(`${apiBase()}/time-entries/ensure-sleep`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ from, to })
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    throw new Error(err.message || `Авто-Сон: HTTP ${res.status}`)
  }
}

/**
 * @param {{ ensureSleep?: boolean }} [opts]
 * ensureSleep (default true) on day open/navigation only — not after place/clear/confirm,
 * otherwise cleared night «Сон» snaps back immediately.
 */
async function loadToday(opts = {}) {
  const headers = authHeaders()
  if (!headers) return
  const doEnsureSleep = opts.ensureSleep !== false
  // Need a date for ensure-sleep; first load without date to resolve "today"
  const qs = selectedDate.value ? `?date=${encodeURIComponent(selectedDate.value)}` : ''
  let res = await fetch(`${apiBase()}/time-entries/today${qs}`, { headers })
  if (!res.ok) throw new Error(`Записи: HTTP ${res.status}`)
  let body = await res.json()
  timezone.value = body.timezone || timezone.value
  selectedDate.value = body.date
  dayStart.value = body.dayStart || ''
  dayEndExclusive.value = body.dayEnd || ''
  if (body.dayEndSetting) dayEndSetting.value = String(body.dayEndSetting).slice(0, 5)

  if (doEnsureSleep) {
    await ensureSleepForDay()
  }

  res = await fetch(
    `${apiBase()}/time-entries/today?date=${encodeURIComponent(selectedDate.value)}`,
    { headers }
  )
  if (!res.ok) throw new Error(`Записи: HTTP ${res.status}`)
  body = await res.json()
  timezone.value = body.timezone || timezone.value
  selectedDate.value = body.date
  dayStart.value = body.dayStart || ''
  dayEndExclusive.value = body.dayEnd || ''
  if (body.dayEndSetting) dayEndSetting.value = String(body.dayEndSetting).slice(0, 5)
  entries.value = body.entries || []
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const stored = localStorage.getItem(SHOW_NIGHT_KEY)
    if (stored === '1') showNightHours.value = true
    if (stored === '0') showNightHours.value = false
    await Promise.all([loadProjects(), loadDelos(), loadSettings()])
    await loadToday({ ensureSleep: true })
    await loadWeekBacklog()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

/** ISO week-year + week number for a YYYY-MM-DD date (Monday-based ISO). */
function isoWeekParts(dateStr) {
  const d = new Date(`${dateStr}T00:00:00`)
  // Thursday of this ISO week determines week-based year
  const day = (d.getDay() + 6) % 7 // Mon=0 … Sun=6
  const thursday = new Date(d)
  thursday.setDate(d.getDate() - day + 3)
  const isoYear = thursday.getFullYear()
  const jan4 = new Date(isoYear, 0, 4)
  const jan4Day = (jan4.getDay() + 6) % 7
  const week1Monday = new Date(jan4)
  week1Monday.setDate(jan4.getDate() - jan4Day)
  const weekNo = 1 + Math.round((thursday - week1Monday) / 604800000)
  return { isoYear, weekNo }
}

async function loadWeekBacklog() {
  backlogLoading.value = true
  backlogError.value = ''
  try {
    const headers = authHeaders()
    if (!headers) return
    let url = `${apiBase()}/backlog/week`
    if (selectedDate.value) {
      const { isoYear, weekNo } = isoWeekParts(selectedDate.value)
      url = `${apiBase()}/backlog/week/${isoYear}/${weekNo}`
    }
    const res = await fetch(url, { headers })
    if (handleAuthFailure(res)) return
    if (!res.ok) throw new Error(`Бэклог недели: HTTP ${res.status}`)
    const body = await res.json()
    weekBacklog.value = body.delos || []
  } catch (e) {
    backlogError.value = e instanceof Error ? e.message : String(e)
  } finally {
    backlogLoading.value = false
  }
}

function toggleNightHours() {
  showNightHours.value = !showNightHours.value
  localStorage.setItem(SHOW_NIGHT_KEY, showNightHours.value ? '1' : '0')
}

function shiftDay(delta) {
  if (!selectedDate.value) return
  const [y, m, d] = selectedDate.value.split('-').map(Number)
  const dt = new Date(y, m - 1, d)
  dt.setDate(dt.getDate() + delta)
  selectedDate.value = `${dt.getFullYear()}-${pad2(dt.getMonth() + 1)}-${pad2(dt.getDate())}`
}

watch(selectedDate, async (val, old) => {
  if (!val || val === old) return
  // avoid double-load on first mount
  if (loading.value) return
  loading.value = true
  error.value = ''
  try {
    await loadToday({ ensureSleep: true })
    await loadWeekBacklog()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
})

async function onCellClick(slot) {
  // Continuations are not rendered as buttons; still guard
  if (slot.isContinuation) return
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
    await loadToday({ ensureSleep: false })
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
    await loadToday({ ensureSleep: false })
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
    await loadToday({ ensureSleep: false })
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    saving.value = false
  }
}

const pendingConfirmCount = computed(() => {
  return entries.value.filter(
    e => e.status === 'PLANNED' && isPastSlot(normalizeStart(e.startAt))
  ).length
})

async function confirmAllDay() {
  if (!selectedDate.value) return
  const headers = authHeaders(true)
  if (!headers) return
  saving.value = true
  error.value = ''
  try {
    const from = `${selectedDate.value}T00:00:00`
    // exclusive end = next day 00:00
    const [y, m, d] = selectedDate.value.split('-').map(Number)
    const next = new Date(y, m - 1, d + 1)
    const toDate = `${next.getFullYear()}-${pad2(next.getMonth() + 1)}-${pad2(next.getDate())}`
    const to = `${toDate}T00:00:00`
    const res = await fetch(`${apiBase()}/time-entries/confirm-all`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ from, to })
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      throw new Error(err.message || `Подтвердить все: HTTP ${res.status}`)
    }
    await loadToday({ ensureSleep: false })
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
  } else {
    const text = pickerAdHoc.value.trim()
    if (!text) {
      error.value = 'Введите текст'
      return
    }
    body = { startAt: pickerSlot.value, adHocText: text }
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
    await loadToday({ ensureSleep: false })
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    saving.value = false
  }
}

function cellClass(slot) {
  const classes = ['grid-cell']
  const e = slot.entry || slot.covering
  if (e) {
    classes.push(e.status === 'DONE' ? 'cell-done' : 'cell-planned')
  } else {
    classes.push('cell-empty')
  }
  if (slot.isNight) classes.push('cell-night')
  if (nowSlotStart.value && normalizeStart(slot.startAt) === normalizeStart(nowSlotStart.value)) {
    classes.push('cell-now')
  }
  if (slot.minute === 0) classes.push('cell-hour')
  if (slot.span > 1) classes.push('cell-span')
  return classes
}

function cellTitle(slot) {
  if (!slot.entry && !slot.covering) {
    return 'Пусто — край блока удлинит соседнее Дело; иначе откроется выбор'
  }
  const e = slot.entry || slot.covering
  const name = e.deloTitle || e.adHocText
  const range = `${parseSlotLabel(e.startAt)}–${parseSlotLabel(e.endAt)}`
  return `${name} ${range} — клик: край −/+15м, середина — разрез`
}

const nightHoursLabel = computed(() => {
  return `${nightStart.value}–${nightEnd.value}`
})

const weekLabel = computed(() => {
  if (!selectedDate.value) return ''
  const { isoYear, weekNo } = isoWeekParts(selectedDate.value)
  return `${isoYear}-W${String(weekNo).padStart(2, '0')}`
})

async function removeFromBacklog(deloId) {
  if (!selectedDate.value) return
  const headers = authHeaders()
  if (!headers) return
  saving.value = true
  error.value = ''
  try {
    const { isoYear, weekNo } = isoWeekParts(selectedDate.value)
    const res = await fetch(`${apiBase()}/backlog/week/${isoYear}/${weekNo}/delos/${deloId}`, {
      method: 'DELETE',
      headers
    })
    if (handleAuthFailure(res)) return
    if (!res.ok) throw new Error(`Удаление из бэклога: HTTP ${res.status}`)
    await loadWeekBacklog()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    saving.value = false
  }
}

onMounted(loadAll)
</script>

<template>
  <div class="today-page">
    <FocusPanel v-if="timeCaptureMode === 'PRIMARY_FOCUS'" />
    <header class="page-header today-header">
      <div>
        <h1>Сегодня</h1>
        <p class="eyebrow">15-минутная сетка · {{ timezone }} · ночь {{ nightHoursLabel }}</p>
      </div>
      <div class="day-nav">
        <button type="button" class="btn btn-ghost" @click="shiftDay(-1)" :disabled="loading" aria-label="Предыдущий день">←</button>
        <input
          class="input day-input"
          type="date"
          v-model="selectedDate"
          :disabled="loading"
        />
        <button type="button" class="btn btn-ghost" @click="shiftDay(1)" :disabled="loading" aria-label="Следующий день">→</button>
        <button
          type="button"
          class="btn btn-ghost"
          @click="selectedDate = new Date().toISOString().slice(0, 10)"
          :disabled="loading"
        >
          Сегодня
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
          @click="confirmAllDay"
        >
          Подтвердить все
          <template v-if="pendingConfirmCount"> ({{ pendingConfirmCount }})</template>
        </button>
      </div>
    </header>

    <div v-if="error" class="alert alert-error" style="margin-bottom: 1rem">{{ error }}</div>
    <div v-if="loading && !slots.length" class="loading">Загрузка…</div>

    <div v-else class="today-layout">
      <section class="card grid-card" aria-label="Сетка дня">
        <div class="grid-scroll">
          <div class="day-grid">
            <button
              v-for="slot in visibleSlots"
              :key="slot.startAt"
              type="button"
              :class="cellClass(slot)"
              :title="cellTitle(slot)"
              :disabled="saving"
              :style="slot.span > 1 ? { minHeight: `calc(${slot.span} * 1.5rem)` } : undefined"
              @click="onCellClick(slot)"
            >
              <span class="cell-time">{{ slot.label }}</span>
              <span v-if="slot.displayLabel" class="cell-label">
                {{ slot.displayLabel }}
              </span>
            </button>
          </div>
        </div>
        <p class="hint grid-legend">
          <span class="legend-swatch planned"></span> запланирована
          <span class="legend-swatch done"></span> выполнена
          · логический день до {{ dayEndSetting }} · ночные ({{ nightHoursLabel }}) скрыты по умолчанию
          · авто «Сон» одним интервалом; клик по краю ±15м, по середине — разрез
          · подтверждение факта: панель / «Подтвердить все»
        </p>
      </section>

      <aside class="card panel-card" aria-label="На сегодня">
        <div class="panel-header">
          <h2>На сегодня</h2>
          <div class="panel-filter">
            <label for="execution-mode-filter" class="visually-hidden">Фильтр по способу исполнения</label>
            <select
              id="execution-mode-filter"
              class="input input-sm"
              v-model="executionModeFilter"
              @change="loadWeekBacklog"
            >
              <option v-for="mode in EXECUTION_MODES" :key="mode.value" :value="mode.value">{{ mode.label }}</option>
            </select>
          </div>
        </div>
        <p class="hint" style="margin-bottom: 1rem">
          Дела и записи, уже стоящие в сетке этого дня.
        </p>
        <ul v-if="panelItems.length" class="today-list">
          <li v-for="item in panelItems" :key="item.id" class="today-list-item">
            <span class="today-list-time">{{ item.time }}</span>
            <div class="today-list-body">
              <strong>{{ item.title }}</strong>
              <span class="today-list-meta" :class="item.status === 'DONE' ? 'meta-done' : 'meta-planned'">
                {{ item.statusLabel }}
                <template v-if="!item.deloId"> · ad-hoc</template>
                <template v-else> · {{ item.executionMode }}</template>
              </span>
            </div>
            <div class="today-list-actions">
              <button
                v-if="item.status === 'PLANNED' && isPastSlot(normalizeStart(item.startAt))"
                type="button"
                class="btn btn-ghost btn-sm"
                title="Подтвердить факт"
                :disabled="saving"
                @click="confirmSlot(item.startAt)"
              >✓</button>
              <button
                type="button"
                class="btn btn-ghost btn-sm"
                title="Снять"
                :disabled="saving"
                @click="clearSlot(item.startAt)"
              >×</button>
            </div>
          </li>
        </ul>
        <p v-else class="hint">Пока пусто — кликните ячейку в сетке.</p>
      </aside>

      <!-- Week Backlog Panel -->
      <aside class="card panel-card" aria-label="Бэклог недели">
        <div class="panel-header">
          <h2>Бэклог недели</h2>
          <div class="panel-filter">
            <label for="backlog-execution-mode-filter" class="visually-hidden">Фильтр по способу исполнения</label>
            <select
              id="backlog-execution-mode-filter"
              class="input input-sm"
              v-model="executionModeFilter"
              @change="loadWeekBacklog"
            >
              <option v-for="mode in EXECUTION_MODES" :key="mode.value" :value="mode.value">{{ mode.label }}</option>
            </select>
          </div>
        </div>
        <p class="hint" style="margin-bottom: 1rem">
          Дела, запланированные на эту неделю. Кликните чтобы добавить в сетку.
        </p>
        <div v-if="backlogLoading" class="loading">Загрузка бэклога…</div>
        <div v-else-if="backlogError" class="alert alert-error">{{ backlogError }}</div>
        <ul v-else-if="filteredWeekBacklog.length" class="backlog-list">
          <li v-for="delo in filteredWeekBacklog" :key="delo.id" class="backlog-list-item">
            <div class="backlog-item-body">
              <strong>{{ delo.title }}</strong>
              <span class="backlog-item-meta">{{ delo.executionMode }}</span>
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
        <p v-else class="hint">Бэклог недели пуст — добавьте Дела через «Дела» или неделю.</p>
        <p class="hint" style="margin-top: 1rem; font-size: 0.75rem;">
          Неделя: {{ weekLabel }}
        </p>
      </aside>
    </div>

    <!-- Picker modal -->
    <div v-if="pickerOpen" class="modal-backdrop" @click.self="closePicker">
      <div class="modal card" role="dialog" aria-modal="true" aria-label="Поставить запись времени">
        <header class="modal-header">
          <h2>Запись времени</h2>
          <p class="hint">{{ pickerSlot ? parseSlotLabel(pickerSlot) : '' }} · {{ selectedDate }}</p>
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
          >Ad-hoc текст</button>
        </div>

        <div v-if="pickerMode === 'delo'" class="form-group">
          <label for="picker-filter">Поиск Дела</label>
          <input id="picker-filter" class="input" v-model="pickerFilter" placeholder="Название…" />
          <div class="delo-picker-list">
            <div v-for="group in delosByProject" :key="group.key" class="delo-group">
              <div class="delo-group-title">{{ group.label }}</div>
              <label
                v-for="d in group.items"
                :key="d.id + '-' + group.key"
                class="delo-option"
                :class="{ selected: String(pickerDeloId) === String(d.id) }"
              >
                <input type="radio" name="picker-delo" :value="d.id" v-model="pickerDeloId" />
                {{ d.title }}
              </label>
            </div>
            <p v-if="!delosByProject.length" class="hint">Нет Дел — создайте на экране «Дела» или используйте ad-hoc.</p>
          </div>
        </div>

        <div v-else class="form-group">
          <label for="picker-adhoc">Текст без создания Дела</label>
          <input
            id="picker-adhoc"
            class="input"
            v-model="pickerAdHoc"
            maxlength="500"
            placeholder="Например: позвонить маме"
            @keyup.enter="submitPicker"
          />
          <p class="hint">Ad-hoc не создаёт Дело в каталоге.</p>
        </div>

        <div class="form-actions">
          <button type="button" class="btn btn-primary" :disabled="saving" @click="submitPicker">
            {{ saving ? 'Сохраняю…' : (isPastSlot(pickerSlot) ? 'Поставить как выполненную' : 'Запланировать') }}
          </button>
          <button type="button" class="btn btn-ghost" :disabled="saving" @click="closePicker">Отмена</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.today-page {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.today-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

.day-nav {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.day-input {
  width: auto;
  min-width: 10rem;
}

.today-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 1.25rem;
  align-items: start;
}

@media (max-width: 900px) {
  .today-layout {
    grid-template-columns: 1fr;
  }
}

.grid-card {
  padding: 1rem 1rem 0.75rem;
}

.grid-scroll {
  max-height: calc(100vh - 260px);
  overflow-y: auto;
  border-radius: 12px;
  border: 1px solid #e6dfd4;
  background: #fdfbf7;
}

.day-grid {
  display: grid;
  grid-template-columns: 1fr;
}

.grid-cell {
  display: grid;
  grid-template-columns: 3.5rem 1fr;
  gap: 0.5rem;
  align-items: center;
  width: 100%;
  min-height: 1.65rem;
  padding: 0.15rem 0.6rem;
  border: none;
  border-bottom: 1px solid #efe8dc;
  background: transparent;
  text-align: left;
  font-family: inherit;
  font-size: 0.8rem;
  color: #2c2a26;
  cursor: pointer;
  transition: background 0.12s;
}

.grid-cell:hover:not(:disabled) {
  background: #f0ebe3;
}

.grid-cell:disabled {
  opacity: 0.7;
  cursor: wait;
}

.cell-hour {
  border-top: 1px solid #d9d1c6;
}

.cell-time {
  font-variant-numeric: tabular-nums;
  color: #8a8278;
  font-size: 0.72rem;
}

.cell-span {
  align-items: flex-start;
}
.cell-span .cell-label {
  white-space: normal;
}
.cell-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.cell-empty .cell-span {
  align-items: flex-start;
}
.cell-span .cell-label {
  white-space: normal;
}
.cell-label {
  color: transparent;
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
  box-shadow: inset 3px 0 0 var(--wolf-ink);
}

.cell-night .cell-time {
  color: #6b6488;
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

.today-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.5rem;
}

.today-list-item {
  display: grid;
  grid-template-columns: 3rem 1fr auto;
  gap: 0.5rem;
  align-items: start;
  padding: 0.5rem 0.6rem;
  border-radius: 10px;
  background: #f7f3ec;
  border: 1px solid #ebe3d6;
}

.today-list-actions {
  display: flex;
  gap: 0.15rem;
  align-items: center;
}

.today-list-time {
  font-variant-numeric: tabular-nums;
  font-size: 0.8rem;
  color: #7a7268;
  padding-top: 0.15rem;
}

.today-list-body {
  display: grid;
  gap: 0.15rem;
  min-width: 0;
}

.today-list-body strong {
  font-size: 0.9rem;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
}

.today-list-meta {
  font-size: 0.75rem;
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

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  flex-wrap: wrap;
  margin-bottom: 0.5rem;
}

.panel-header h2 {
  margin: 0;
  flex-shrink: 1;
  min-width: 0;
}

.panel-filter {
  flex-shrink: 0;
}

.input-sm {
  font-size: 0.75rem;
  padding: 0.25rem 2rem 0.25rem 0.5rem;
  min-width: 120px;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.backlog-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.4rem;
}

.backlog-list-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 0.5rem;
  align-items: center;
  padding: 0.4rem 0.5rem;
  border-radius: 8px;
  background: #f7f3ec;
  border: 1px solid #ebe3d6;
}

.backlog-item-body {
  display: grid;
  gap: 0.1rem;
  min-width: 0;
}

.backlog-item-body strong {
  font-size: 0.85rem;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
}

.backlog-item-meta {
  font-size: 0.7rem;
  color: #8a8278;
  text-transform: capitalize;
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
/* Ticket 01: normalize existing grid/panel rules without altering TimeEntry geometry. */
.grid-scroll,
.delo-picker-list { border-color: var(--wolf-rule); border-radius: 0; background: var(--wolf-surface); }
.grid-cell { border-bottom-color: var(--wolf-subrule); color: var(--wolf-ink); }
.grid-cell.cell-planned { background: #f7f8fa; }
.grid-cell.cell-done { background: var(--wolf-done-surface); }
.grid-cell:hover:not(:disabled),
.delo-option:hover,
.delo-option.selected { background: var(--wolf-hover); }
.cell-hour { border-top-color: var(--wolf-rule); }
.cell-time,
.today-list-time,
.today-list-meta,
.backlog-item-meta { color: var(--wolf-muted); }
.cell-night .cell-time { color: var(--wolf-muted); }
.today-list-item,
.backlog-list-item { border-color: var(--wolf-rule); border-radius: 0; background: transparent; }
.legend-swatch { border-color: var(--wolf-rule); border-radius: 0; }
.modal { border-radius: 0; box-shadow: none; }
</style>
