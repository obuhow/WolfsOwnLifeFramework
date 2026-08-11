<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { apiBase } from '../api'

const loading = ref(false)
const error = ref('')
const timezone = ref('Europe/Moscow')
const selectedDate = ref('')
const entries = ref([])
const delos = ref([])
const projects = ref([])

// Picker state
const pickerOpen = ref(false)
const pickerSlot = ref(null) // LocalDateTime string
const pickerMode = ref('delo') // 'delo' | 'adhoc'
const pickerDeloId = ref('')
const pickerAdHoc = ref('')
const pickerFilter = ref('')
const saving = ref(false)

const SLOTS_PER_DAY = 96 // 00:00 .. 23:45

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
  // "2026-08-11T10:15:00" or "2026-08-11T10:15"
  const t = startAt.includes('T') ? startAt.split('T')[1] : startAt
  return t.slice(0, 5)
}

const slots = computed(() => {
  if (!selectedDate.value) return []
  const byStart = new Map(entries.value.map(e => [normalizeStart(e.startAt), e]))
  const list = []
  for (let i = 0; i < SLOTS_PER_DAY; i++) {
    const startAt = slotStartAt(selectedDate.value, i)
    const entry = byStart.get(normalizeStart(startAt)) || null
    const totalMin = i * 15
    list.push({
      index: i,
      startAt,
      label: formatTime(Math.floor(totalMin / 60), totalMin % 60),
      hour: Math.floor(totalMin / 60),
      minute: totalMin % 60,
      entry
    })
  }
  return list
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
      time: parseSlotLabel(e.startAt),
      statusLabel: e.status === 'DONE' ? 'выполнена' : 'запланирована'
    }))
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

async function loadToday() {
  const headers = authHeaders()
  if (!headers) return
  const qs = selectedDate.value ? `?date=${encodeURIComponent(selectedDate.value)}` : ''
  const res = await fetch(`${apiBase()}/time-entries/today${qs}`, { headers })
  if (!res.ok) throw new Error(`Записи: HTTP ${res.status}`)
  const body = await res.json()
  timezone.value = body.timezone || 'Europe/Moscow'
  selectedDate.value = body.date
  entries.value = body.entries || []
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    await Promise.all([loadProjects(), loadDelos()])
    await loadToday()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
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
    await loadToday()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
})

function onCellClick(slot) {
  if (slot.entry) {
    // Toggle clear
    clearSlot(slot.startAt)
    return
  }
  openPicker(slot.startAt)
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
    await loadToday()
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
    const res = await fetch(`${apiBase()}/time-entries`, {
      method: 'PUT',
      headers,
      body: JSON.stringify(body)
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      throw new Error(err.message || `Сохранение: HTTP ${res.status}`)
    }
    closePicker()
    await loadToday()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    saving.value = false
  }
}

function cellClass(slot) {
  const classes = ['grid-cell']
  if (slot.entry) {
    classes.push(slot.entry.status === 'DONE' ? 'cell-done' : 'cell-planned')
  } else {
    classes.push('cell-empty')
  }
  if (nowSlotStart.value && normalizeStart(slot.startAt) === normalizeStart(nowSlotStart.value)) {
    classes.push('cell-now')
  }
  if (slot.minute === 0) classes.push('cell-hour')
  return classes
}

function cellTitle(slot) {
  if (!slot.entry) return 'Пусто — нажмите, чтобы поставить Запись времени'
  const name = slot.entry.deloTitle || slot.entry.adHocText
  const st = slot.entry.status === 'DONE' ? 'выполнена' : 'запланирована'
  return `${name} (${st}) — нажмите, чтобы снять`
}

onMounted(loadAll)
</script>

<template>
  <div class="today-page">
    <header class="page-header today-header">
      <div>
        <h1>Сегодня</h1>
        <p class="eyebrow">15-минутная сетка · {{ timezone }}</p>
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
      </div>
    </header>

    <div v-if="error" class="alert alert-error" style="margin-bottom: 1rem">{{ error }}</div>
    <div v-if="loading && !slots.length" class="loading">Загрузка…</div>

    <div v-else class="today-layout">
      <section class="card grid-card" aria-label="Сетка дня">
        <div class="grid-scroll">
          <div class="day-grid">
            <button
              v-for="slot in slots"
              :key="slot.startAt"
              type="button"
              :class="cellClass(slot)"
              :title="cellTitle(slot)"
              :disabled="saving"
              @click="onCellClick(slot)"
            >
              <span class="cell-time">{{ slot.label }}</span>
              <span v-if="slot.entry" class="cell-label">
                {{ slot.entry.deloTitle || slot.entry.adHocText }}
              </span>
            </button>
          </div>
        </div>
        <p class="hint grid-legend">
          <span class="legend-swatch planned"></span> запланирована
          <span class="legend-swatch done"></span> выполнена
          · пустая ячейка: будущее → план, прошлое → факт · повторный клик снимает
        </p>
      </section>

      <aside class="card panel-card" aria-label="На сегодня">
        <h2>На сегодня</h2>
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
              </span>
            </div>
            <button
              type="button"
              class="btn btn-ghost btn-sm"
              title="Снять"
              :disabled="saving"
              @click="clearSlot(item.startAt)"
            >×</button>
          </li>
        </ul>
        <p v-else class="hint">Пока пусто — кликните ячейку в сетке.</p>
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

.cell-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.cell-empty .cell-label {
  color: transparent;
}

.cell-planned {
  background: #e8f0ea;
}

.cell-planned:hover:not(:disabled) {
  background: #dce8df;
}

.cell-done {
  background: #e4eef6;
}

.cell-done:hover:not(:disabled) {
  background: #d5e4f0;
}

.cell-now {
  box-shadow: inset 3px 0 0 #3d5a4a;
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
  background: #e8f0ea;
}

.legend-swatch.done {
  background: #e4eef6;
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
  color: #3d5a4a;
}

.meta-done {
  color: #3a5a7a;
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
  background: #e8f0ea;
}

.delo-option input {
  accent-color: #3d5a4a;
}
</style>
