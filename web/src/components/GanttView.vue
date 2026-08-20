<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { apiBase, authHeaders, handleAuthFailure } from '../api'

const loading = ref(false)
const error = ref('')
const success = ref('')
const saving = ref(false)

const hourAccountingMode = ref('PRIMARY_ONLY')
const timezone = ref('Europe/Moscow')
const weeks = ref([])
const projects = ref([])
const forecasts = ref([])
const areas = ref([])

const weekCount = ref(16)
const rangeFrom = ref('') // Monday YYYY-MM-DD; empty = API default (prev week Mon)
const selectedAreaIds = ref([]) // multi-select checkboxes; empty = all
const onlyWithDates = ref(false)

// Inline plan edit
const editKey = ref('') // `${projectId}|${isoYear}|${isoWeek}`
const editValue = ref('')

const MODE_LABEL = {
  PRIMARY_ONLY: 'только основной проект',
  ALL_PROJECTS: 'все проекты'
}

function auth(json = false) {
  return authHeaders(json)
}

async function loadAreas() {
  const headers = auth()
  if (!headers) return
  const res = await fetch(`${apiBase()}/life-areas`, { headers })
  if (handleAuthFailure(res)) return
  if (!res.ok) throw new Error(`Области: HTTP ${res.status}`)
  areas.value = await res.json()
}

async function loadGantt() {
  const headers = auth()
  if (!headers) return
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams()
    params.set('weeks', String(weekCount.value || 16))
    if (rangeFrom.value) params.set('from', rangeFrom.value)
    if (selectedAreaIds.value.length) {
      params.set('lifeAreaIds', selectedAreaIds.value.join(','))
    }
    if (onlyWithDates.value) params.set('onlyWithDates', 'true')

    const res = await fetch(`${apiBase()}/gantt?${params}`, { headers })
    if (handleAuthFailure(res)) return
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      throw new Error(err.message || `Гантт: HTTP ${res.status}`)
    }
    const body = await res.json()
    hourAccountingMode.value = body.hourAccountingMode || 'PRIMARY_ONLY'
    timezone.value = body.timezone || timezone.value
    weeks.value = body.weeks || []
    projects.value = body.projects || []

    const forecastRes = await fetch(`${apiBase()}/gantt/forecast`, { headers })
    if (handleAuthFailure(forecastRes)) return
    if (!forecastRes.ok) {
      const err = await forecastRes.json().catch(() => ({}))
      throw new Error(err.message || `Прогноз: HTTP ${forecastRes.status}`)
    }
    forecasts.value = await forecastRes.json()
    if (!rangeFrom.value && body.rangeStart) {
      rangeFrom.value = body.rangeStart
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function loadAll() {
  try {
    await loadAreas()
    await loadGantt()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

const monthSpans = computed(() => {
  const cols = weeks.value
  if (!cols.length) return []
  const spans = []
  let i = 0
  while (i < cols.length) {
    const key = `${cols[i].calendarYear}-${cols[i].month}`
    let j = i + 1
    while (j < cols.length && `${cols[j].calendarYear}-${cols[j].month}` === key) j++
    spans.push({
      label: cols[i].monthLabel,
      year: cols[i].calendarYear,
      span: j - i,
      startIndex: i
    })
    i = j
  }
  return spans
})

function toggleArea(id) {
  const sid = String(id)
  const idx = selectedAreaIds.value.indexOf(sid)
  if (idx >= 0) selectedAreaIds.value.splice(idx, 1)
  else selectedAreaIds.value.push(sid)
  loadGantt()
}

function isAreaSelected(id) {
  return selectedAreaIds.value.includes(String(id))
}

function shiftWeeks(delta) {
  if (!rangeFrom.value) return
  const [y, m, d] = rangeFrom.value.split('-').map(Number)
  const dt = new Date(y, m - 1, d)
  dt.setDate(dt.getDate() + delta * 7)
  const pad = n => String(n).padStart(2, '0')
  rangeFrom.value = `${dt.getFullYear()}-${pad(dt.getMonth() + 1)}-${pad(dt.getDate())}`
  loadGantt()
}

function resetToDefault() {
  rangeFrom.value = ''
  loadGantt()
}

function cellKey(projectId, isoYear, isoWeek) {
  return `${projectId}|${isoYear}|${isoWeek}`
}

function startEdit(project, cell) {
  editKey.value = cellKey(project.id, cell.isoYear, cell.isoWeek)
  editValue.value = cell.planHours != null ? String(cell.planHours) : ''
}

function cancelEdit() {
  editKey.value = ''
  editValue.value = ''
}

function isEditing(projectId, isoYear, isoWeek) {
  return editKey.value === cellKey(projectId, isoYear, isoWeek)
}

async function commitEdit(project, cell) {
  const headers = auth(true)
  if (!headers) return
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const raw = editValue.value.trim().replace(',', '.')
    let planHours = null
    if (raw !== '') {
      const n = Number(raw)
      if (Number.isNaN(n) || n < 0) {
        throw new Error('Введите неотрицательное число часов')
      }
      planHours = n
    } else {
      planHours = 0 // clear
    }

    const res = await fetch(`${apiBase()}/gantt/week-plans`, {
      method: 'PUT',
      headers,
      body: JSON.stringify({
        projectId: project.id,
        isoYear: cell.isoYear,
        isoWeek: cell.isoWeek,
        planHours
      })
    })
    if (handleAuthFailure(res)) return
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      throw new Error(err.message || `План: HTTP ${res.status}`)
    }
    const body = await res.json()
    // Patch local cell
    cell.planHours = body.planHours
    cancelEdit()
    success.value = 'План сохранён'
    setTimeout(() => { success.value = '' }, 1500)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    saving.value = false
  }
}

function formatHours(v) {
  if (v == null || v === '') return '·'
  const n = Number(v)
  if (Number.isNaN(n)) return '·'
  if (n === 0) return '0'
  return Number.isInteger(n) ? String(n) : n.toFixed(1).replace(/\.0$/, '')
}

function projectPlanTotal(project) {
  if (project.totalPlanHours != null) return project.totalPlanHours
  return project.cells.reduce((sum, cell) => sum + (Number(cell.planHours) || 0), 0)
}

function projectFactTotal(project) {
  return project.cells.reduce((sum, cell) => sum + (Number(cell.factHours) || 0), 0)
}

function planBarWidth(hours) {
  const n = Number(hours) || 0
  // 40h/week ≈ full strip
  return Math.min(100, Math.round((n / 40) * 100))
}

function factBarWidth(hours) {
  const n = Number(hours) || 0
  return Math.min(100, Math.round((n / 40) * 100))
}

function forecastFor(projectId) {
  return forecasts.value.find(item => item.projectId === projectId) || null
}

function forecastBarWidth(project, week) {
  const forecast = forecastFor(project.id)
  if (!forecast || !forecast.forecastEnd) return 0
  const start = weeks.value.find(item => item.current)?.weekStart || week.weekStart
  if (start >= week.weekEndExclusive || forecast.forecastEnd <= week.weekStart) return 0
  const weekStartMs = Date.parse(week.weekStart)
  const weekEndMs = Date.parse(week.weekEndExclusive)
  const overlapStart = Math.max(Date.parse(start), weekStartMs)
  const overlapEnd = Math.min(Date.parse(forecast.forecastEnd), weekEndMs)
  return Math.max(0, Math.min(100, Math.round(((overlapEnd - overlapStart) / (weekEndMs - weekStartMs)) * 100)))
}

function forecastTitle(project) {
  const forecast = forecastFor(project.id)
  if (!forecast || !forecast.forecastEnd) return 'Данных для прогноза пока мало'
  return `Прогноз: ${forecast.forecastEnd} · среднее ${formatHours(forecast.weeklyAvg)} ч/нед · осталось ${formatHours(forecast.remaining)} ч`
}

function hasDateStrip(project, week) {
  if (!project.startDate && !project.endDate) return false
  const wStart = week.weekStart
  const wEnd = week.weekEndExclusive
  const s = project.startDate || '0001-01-01'
  const e = project.endDate || '9999-12-31'
  // overlap of [s,e] with [wStart, wEnd)
  return s < wEnd && e >= wStart
}

function dateStripClass(project, week) {
  if (!hasDateStrip(project, week)) return ''
  return 'date-strip-on'
}

const rangeLabel = computed(() => {
  if (!weeks.value.length) return ''
  const a = weeks.value[0]
  const b = weeks.value[weeks.value.length - 1]
  return `${a.weekStart} → ${b.weekEndExclusive}`
})

watch([weekCount, onlyWithDates], () => {
  loadGantt()
})

onMounted(loadAll)
</script>

<template>
  <div class="gantt-page">
    <header class="page-header gantt-header">
      <div>
        <h1>Планирование</h1>
        <p class="eyebrow">План и факт по Проектам · {{ MODE_LABEL[hourAccountingMode] || hourAccountingMode }}</p>
      </div>
      <div class="gantt-toolbar">
        <button type="button" class="btn btn-ghost" :disabled="loading" @click="shiftWeeks(-4)" title="На 4 недели назад">←</button>
        <button type="button" class="btn btn-ghost" :disabled="loading" @click="shiftWeeks(-1)">‹</button>
        <button type="button" class="btn btn-ghost" :disabled="loading" @click="resetToDefault">Сброс</button>
        <button type="button" class="btn btn-ghost" :disabled="loading" @click="shiftWeeks(1)">›</button>
        <button type="button" class="btn btn-ghost" :disabled="loading" @click="shiftWeeks(4)" title="На 4 недели вперёд">→</button>
        <label class="weeks-select">
          Недель
          <select v-model.number="weekCount" :disabled="loading">
            <option :value="8">8</option>
            <option :value="12">12</option>
            <option :value="16">16</option>
            <option :value="24">24</option>
            <option :value="32">32</option>
          </select>
        </label>
        <button type="button" class="btn btn-ghost" :disabled="loading" @click="loadGantt">Обновить</button>
      </div>
    </header>

    <p v-if="error" class="banner error">{{ error }}</p>
    <p v-if="success" class="banner ok">{{ success }}</p>

    <section class="gantt-filters card">
      <div class="filter-block">
        <span class="filter-label">Области жизни</span>
        <div class="filter-chips">
          <label
            v-for="a in areas"
            :key="a.id"
            class="chip"
            :class="{ on: isAreaSelected(a.id) }"
          >
            <input
              type="checkbox"
              :checked="isAreaSelected(a.id)"
              @change="toggleArea(a.id)"
            />
            <span class="chip-dot" :style="{ background: a.color || '#3d5a4a' }"></span>
            {{ a.name }}
          </label>
          <span v-if="!areas.length" class="muted">Нет областей</span>
        </div>
      </div>
      <label class="filter-check">
        <input v-model="onlyWithDates" type="checkbox" />
        Только с сроками
      </label>
      <p class="muted range-hint">{{ rangeLabel }} · {{ timezone }}</p>
    </section>

    <div v-if="loading && !projects.length" class="muted">Загрузка…</div>

    <div v-else class="gantt-scroll">
      <div
        v-if="weeks.length"
        class="gantt-grid"
        :style="{ '--week-count': weeks.length }"
      >
        <div class="gantt-header-grid">
          <div class="sticky-col corner">Проект</div>
          <div
            v-for="(m, mi) in monthSpans"
            :key="mi"
            class="month-cell"
            :style="{ gridColumn: `${m.startIndex + 2} / span ${m.span}` }"
          >
            {{ m.label }} {{ m.year }}
          </div>
          <div class="header-corner-spacer"></div>
          <div
            v-for="(w, wi) in weeks"
            :key="`${w.isoYear}-W${w.isoWeek}`"
            class="week-head"
            :class="{ current: w.current }"
            :title="`${w.weekStart} — ${w.weekEndExclusive}`"
            :style="{ gridColumn: wi + 2 }"
          >
            <span class="w-num">W{{ String(w.isoWeek).padStart(2, '0') }}</span>
            <span class="w-date">{{ w.weekStart.slice(5).replace('-', '.') }}</span>
          </div>
        </div>

        <div v-if="!projects.length" class="empty-row muted">
          Нет проектов по выбранным фильтрам
        </div>
        <div v-for="p in projects" :key="p.id" class="gantt-project-row">
          <div class="sticky-col project-name" :style="{ paddingLeft: `${0.75 + p.depth * 1.1}rem` }">
            <router-link :to="`/projects/${p.id}`" class="project-link">
              <span v-if="p.depth" class="depth-mark">└</span>
              {{ p.title }}
            </router-link>
            <span class="area-tag" :title="p.lifeAreaName">{{ p.lifeAreaName }}</span>
            <span class="project-hours">план {{ formatHours(projectPlanTotal(p)) }} ч · факт {{ formatHours(projectFactTotal(p)) }} ч</span>
          </div>
          <div
            v-for="c in p.cells"
            :key="`${p.id}-${c.isoYear}-${c.isoWeek}`"
            class="gantt-cell"
            :class="{
              current: weeks.find(w => w.isoYear === c.isoYear && w.isoWeek === c.isoWeek)?.current,
              [dateStripClass(p, weeks.find(w => w.isoYear === c.isoYear && w.isoWeek === c.isoWeek) || {})]: true
            }"
            @dblclick="startEdit(p, c)"
          >
            <div
              v-if="hasDateStrip(p, weeks.find(w => w.isoYear === c.isoYear && w.isoWeek === c.isoWeek) || {})"
              class="date-bar"
              title="Сроки проекта"
            ></div>
            <div class="strips">
              <div class="strip plan" :title="`План: ${formatHours(c.planHours)} ч`">
                <div class="bar" :style="{ width: planBarWidth(c.planHours) + '%' }"></div>
                <span class="val plan-val" @click.stop="startEdit(p, c)">
                  <template v-if="isEditing(p.id, c.isoYear, c.isoWeek)">
                    <input
                      v-model="editValue"
                      class="plan-input"
                      type="text"
                      inputmode="decimal"
                      :disabled="saving"
                      @keydown.enter.prevent="commitEdit(p, c)"
                      @keydown.esc.prevent="cancelEdit"
                      @blur="commitEdit(p, c)"
                      autofocus
                    />
                  </template>
                  <template v-else>
                    {{ c.planHours != null ? formatHours(c.planHours) : '·' }}
                  </template>
                </span>
              </div>
              <div class="strip forecast" :title="forecastTitle(p)">
                <div
                  class="bar"
                  :style="{ width: forecastBarWidth(p, weeks.find(w => w.isoYear === c.isoYear && w.isoWeek === c.isoWeek) || {}) + '%' }"
                ></div>
                <span class="val forecast-val">
                  {{ forecastFor(p.id)?.forecastEnd && weeks.find(w => w.weekStart <= forecastFor(p.id).forecastEnd && forecastFor(p.id).forecastEnd < w.weekEndExclusive)?.weekStart === (weeks.find(w => w.isoYear === c.isoYear && w.isoWeek === c.isoWeek)?.weekStart) ? forecastFor(p.id).forecastEnd : '' }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <p class="legend muted">
      Верхняя полоска — <strong>план</strong> (клик / двойной клик — правка часов), нижняя — <strong>прогноз</strong>.
      Наведите на прогноз, чтобы увидеть среднее за 4 недели и остаток. Если данных мало, отображается нейтральное пояснение.
    </p>
  </div>
</template>

<style scoped>
.gantt-page {
  width: 100%;
  max-width: none;
  margin: 0;
}

.gantt-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

.gantt-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
}

.weeks-select {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.85rem;
  color: #5c564e;
}

.weeks-select select {
  font: inherit;
  padding: 0.25rem 0.4rem;
  border-radius: 8px;
  border: 1px solid #d9d1c6;
  background: #fffefb;
}

.gantt-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 1.25rem 1.75rem;
  margin-bottom: 1rem;
  padding: 0.9rem 1.2rem;
}

.filter-block {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 0.75rem;
}

.filter-label {
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #7a7268;
  font-weight: 600;
}

.filter-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.25rem 0.65rem;
  border: 0;
  border-bottom: 1px solid var(--wolf-rule);
  border-radius: 0;
  background: var(--wolf-surface);
  font-size: 0.85rem;
  cursor: pointer;
  user-select: none;
}

.chip input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.chip.on {
  border-color: #3d5a4a;
  background: #e8f0eb;
  color: #2f4a3c;
}

.chip-dot {
  width: 0.55rem;
  height: 0.55rem;
  border-radius: 50%;
}

.filter-check {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.9rem;
  color: #5c564e;
  cursor: pointer;
}

.range-hint {
  margin: 0;
  margin-left: auto;
  font-size: 0.8rem;
}

.gantt-scroll {
  width: 100%;
  max-width: 100%;
  margin: 0;
  overflow-x: auto;
  border-top: 1px solid #e6dfd4;
  border-bottom: 1px solid #e6dfd4;
  background: rgba(255, 252, 247, 0.7);
}

.gantt-header-grid,
.gantt-project-row {
  display: grid;
  grid-template-columns: 12rem repeat(var(--week-count), 4.2rem);
}

.gantt-grid {
  display: block;
  width: max-content;
  min-width: 100%;
  font-size: 0.8rem;
}

.gantt-header-grid {
  position: sticky;
  top: 0;
  z-index: 3;
}

.header-corner-spacer {
  display: none;
}

.gantt-header-grid .corner,
.gantt-header-grid .month-cell,
.gantt-header-grid .week-head,
.gantt-project-row > * {
  box-sizing: border-box;
  border-right: 1px solid #efe8dd;
  border-bottom: 1px solid #efe8dd;
}

.gantt-header-grid .corner {
  grid-row: 1 / span 2;
}

.gantt-header-grid .month-cell {
  grid-row: 1;
  text-align: center;
  font-weight: 600;
  font-size: 0.78rem;
  letter-spacing: 0.04em;
  color: #5c564e;
  background: #f7f2ea;
  padding: 0.35rem 0.25rem;
}

.gantt-header-grid .week-head {
  grid-row: 2;
  text-align: center;
  padding: 0.3rem 0.15rem;
  background: #faf6f0;
  font-weight: 500;
}

.gantt-project-row {
  align-items: stretch;
  gap: 0;
  padding: 0;
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
  transition: none;
}

.gantt-project-row > .sticky-col {
  grid-column: 1;
}

.gantt-project-row > .gantt-cell {
  min-width: 0;
}

.gantt-grid .gantt-cell,
.gantt-grid .sticky-col {
  border-right: 1px solid #efe8dd;
  border-bottom: 1px solid #efe8dd;
}

.gantt-grid .gantt-cell {
  padding: 0.2rem 0.2rem 0.25rem;
  cursor: default;
  position: relative;
  background: #fffefb;
}

.gantt-grid .sticky-col {
  box-shadow: 2px 0 0 #e6dfd4;
}

.gantt-grid .week-head.current,
.gantt-grid .gantt-cell.current {
  background: #eef5f0;
}

.gantt-grid .gantt-cell.current {
  background: #f3f8f5;
}

.gantt-grid .week-head.current {
  box-shadow: inset 0 -2px 0 #3d5a4a;
  color: #2f4a3c;
  font-weight: 700;
}

.gantt-grid .empty-row {
  grid-column: 1 / -1;
}

.gantt-grid .month-cell,
.gantt-grid .week-head {
  position: relative;
  z-index: 1;
}

.gantt-grid .corner {
  z-index: 2;
}

.gantt-grid .sticky-col {
  width: auto;
  min-width: 0;
  max-width: none;
}

.gantt-grid .gantt-cell {
  width: auto;
}

/* Keep legacy table selectors harmless if an old cached DOM is still present. */
.gantt-table {
  width: 100%;
  font-size: 0.8rem;
}

.month-row .month-cell {
  text-align: center;
  font-weight: 600;
  font-size: 0.78rem;
  letter-spacing: 0.04em;
  color: #5c564e;
  background: #f7f2ea;
  padding: 0.35rem 0.25rem;
  position: sticky;
  top: 0;
  z-index: 3;
}

.week-row .week-head {
  text-align: center;
  padding: 0.3rem 0.15rem;
  background: #faf6f0;
  font-weight: 500;
  position: sticky;
  top: 1.55rem;
  z-index: 3;
}

.week-head.current,
.gantt-cell.current {
  background: #eef5f0;
}

.week-head.current {
  box-shadow: inset 0 -2px 0 #3d5a4a;
  color: #2f4a3c;
  font-weight: 700;
}

.w-num {
  display: block;
  font-size: 0.72rem;
}

.w-date {
  display: block;
  font-size: 0.65rem;
  color: #8a8278;
  font-weight: 400;
}

.sticky-col {
  position: sticky;
  left: 0;
  z-index: 4;
  background: #faf6f0;
  text-align: left;
  min-width: 12rem;
  max-width: 18rem;
  width: 12rem;
  padding: 0.45rem 0.75rem;
  box-shadow: 2px 0 0 #e6dfd4;
}

.corner {
  z-index: 5;
  top: 0;
  vertical-align: bottom;
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: #7a7268;
  background: #faf6f0;
  width: 12rem;
  min-width: 12rem;
  max-width: 18rem;
  padding: 0.45rem 0.75rem;
  box-shadow: 2px 0 0 #e6dfd4;
}

.project-name {
  font-weight: 500;
  background: rgba(255, 252, 247, 0.98);
}

.project-link {
  color: inherit;
  text-decoration: none;
}

.project-link:hover {
  color: #3d5a4a;
  text-decoration: underline;
}

.depth-mark {
  color: #b0a89c;
  margin-right: 0.15rem;
}

.area-tag {
  display: block;
  font-size: 0.68rem;
  color: #9a9288;
  font-weight: 400;
  margin-top: 0.1rem;
}

.project-hours {
  display: block;
  font-size: 0.68rem;
  color: #7a7268;
  font-weight: 400;
  margin-top: 0.12rem;
}

.gantt-cell {
  padding: 0.2rem 0.2rem 0.25rem;
  cursor: default;
  position: relative;
  min-width: 3.4rem;
  background: #fffefb;
}

.gantt-cell.current {
  background: #f3f8f5;
}

.date-bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, #c4a574, #8b7355);
  opacity: 0.85;
}

.strips {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-height: 2.4rem;
  justify-content: center;
}

.strip {
  position: relative;
  height: 1.05rem;
  border-radius: 4px;
  background: #f0ebe3;
  overflow: hidden;
}

.strip .bar {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: 4px;
  opacity: 0.85;
  min-width: 0;
  transition: width 0.15s ease;
}

.strip.plan .bar {
  background: #a8c4b4;
}

.strip.fact .bar {
  background: #6b8f7a;
}

.strip .val {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 0.68rem;
  font-variant-numeric: tabular-nums;
  color: #3d3830;
  cursor: pointer;
}

.fact-val {
  cursor: default;
  color: #2f4a3c;
  font-weight: 600;
}

.plan-val {
  color: #4a5e52;
}

.plan-input {
  width: 100%;
  height: 100%;
  border: none;
  background: #fff;
  text-align: center;
  font: inherit;
  font-size: 0.7rem;
  padding: 0;
  outline: 1px solid #3d5a4a;
  border-radius: 3px;
}

.empty-row {
  text-align: center;
  padding: 2rem 1rem;
}

.legend {
  margin-top: 0.85rem;
  font-size: 0.8rem;
  line-height: 1.45;
}

.banner {
  padding: 0.6rem 0.9rem;
  border-radius: 10px;
  margin: 0 0 0.75rem;
  font-size: 0.9rem;
}

.banner.error {
  background: #f8ebe6;
  color: #7a3e2e;
  border: 1px solid #e8cfc4;
}

.banner.ok {
  background: #e8f0eb;
  color: #2f4a3c;
  border: 1px solid #c5d9cc;
}

.muted {
  color: #8a8278;
}

/* Ticket 01 override: the existing data geometry stays intact; only the shared
   register surface, controls and status semantics are normalized here. */
.gantt-page :is(.gantt-filters, .gantt-scroll, .gantt-header-grid .month-cell, .gantt-header-grid .week-head, .sticky-col, .corner, .project-name, .gantt-cell, .month-row .month-cell, .week-row .week-head) {
  background: var(--wolf-surface);
}
.gantt-page :is(.gantt-scroll, .gantt-header-grid .corner, .gantt-header-grid .month-cell, .gantt-header-grid .week-head, .gantt-project-row > *, .gantt-grid .gantt-cell, .gantt-grid .sticky-col) {
  border-color: var(--wolf-rule);
}
.gantt-page :is(.gantt-grid .sticky-col, .sticky-col, .corner) { box-shadow: 1px 0 0 var(--wolf-rule); }
.gantt-page :is(.chip, .chip.on) {
  border: 0;
  border-bottom: 1px solid var(--wolf-rule);
  border-radius: 0;
  background: var(--wolf-surface);
  color: var(--wolf-ink);
}
.gantt-page .chip.on { border-bottom-color: var(--wolf-ink); }
.gantt-page .chip-dot { border-radius: 0; }
.gantt-page :is(.gantt-grid .week-head.current, .gantt-grid .gantt-cell.current, .week-head.current, .gantt-cell.current) {
  background: #f7f7f5;
  color: var(--wolf-ink);
}
.gantt-page :is(.gantt-grid .week-head.current, .week-head.current) { box-shadow: inset 0 -2px 0 var(--wolf-ink); }
.gantt-page .date-bar { background: var(--wolf-ink); opacity: .35; }
.gantt-page .strip { border-radius: 0; background: var(--wolf-subrule); }
.gantt-page .strip .bar { border-radius: 0; }
.gantt-page .strip.plan .bar { background: var(--wolf-ink); }
.gantt-page .strip.fact .bar { background: var(--wolf-done-ink); }
.gantt-page .strip.forecast .bar { background: var(--wolf-muted); }
.gantt-page :is(.strip .val, .plan-val) { color: var(--wolf-ink); }
.gantt-page .fact-val { color: var(--wolf-done-ink); }
.gantt-page .forecast-val { color: var(--wolf-muted); font-size: 0.58rem; }
.gantt-page .plan-input { background: var(--wolf-surface); outline-color: var(--wolf-ink); border-radius: 0; }
.gantt-page .project-link:hover { color: var(--wolf-ink); }
.gantt-page :is(.w-date, .area-tag, .depth-mark, .muted, .filter-check) { color: var(--wolf-muted); }
.gantt-page .banner { margin: 0 0 12px; border-radius: 0; background: transparent; }
.gantt-page .banner.error { color: var(--wolf-ink); border-color: var(--wolf-ink); }
.gantt-page .banner.ok { color: var(--wolf-done-ink); border-color: var(--wolf-done-ink); }

@media (max-width: 720px) {
  .sticky-col {
    min-width: 8.5rem;
  }
}
</style>
