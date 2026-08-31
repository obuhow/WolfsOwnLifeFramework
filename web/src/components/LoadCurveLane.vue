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
  along with this program. if not see <https://www.gnu.org/licenses/>.
-->
<script setup>
/*
 * Дорожка кривой нагрузки одного проекта (release 0.8, тикет 06).
 *
 * Переиспользуемая геометрия и взаимодействие из вкладки «Кривые» (тикет 03),
 * но работает АВТОНОМНО — без inject('loadCharts'): сама грузит
 * GET /api/v1/projects/{id}/load-curve и пишет через useLoadChartWrite
 * (PUT/DELETE /api/v1/projects/{id}/load-curve). Используется и в модалке
 * страницы проекта (тикет 06), и во вкладке «Кривые» (CurvesTab рендерит
 * список таких дорожек).
 *
 * startMonday / horizonMonths нужны только для геометрии. Если не переданы
 * пропсами (страница проекта), подтягиваются одним GET /planning/load-charts
 * (берём только startMonday + horizonMonths, кривую всё равно грузим отдельно).
 *
 * Геометрия идентична CurvesTab: позиция точки = monthIndex/horizon × 100% по
 * горизонтали, высота = hours/HOURS_MAX × 100% по вертикали. Запись — дебаунс
 * 400 мс после жеста, откат к серверному при ошибке PUT.
 */
import { computed, onMounted, ref } from 'vue'
import { apiBase, authHeaders } from '../api'
import { useLoadChartWrite } from '../loadChartsWrite'

const props = defineProps({
  projectId: { type: [Number, String], required: true },
  title: { type: String, default: '' },
  startMonday: { type: String, default: null },
  horizonMonths: { type: Number, default: 18 },
  // опционально: кривая передаётся снаружи (из load-charts), иначе грузим сами
  curve: { type: Array, default: null },
})

const HOURS_MAX = 20 // верхняя граница шкалы часов (0–20)

// Разрешённые значения геометрии (пропсы либо подтянуты сами).
const startMondayR = ref(props.startMonday)
const horizonR = ref(props.horizonMonths)
const loading = ref(false)
const error = ref('')
const localCurve = ref([])

const months = computed(() => {
  if (!startMondayR.value) return []
  const out = []
  const d = new Date(startMondayR.value + 'T00:00:00')
  for (let i = 0; i < horizonR.value; i++) {
    out.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`)
    d.setMonth(d.getMonth() + 1)
  }
  return out
})

function monthIndexFromDate(iso) {
  if (!iso || !startMondayR.value) return -1
  const from = new Date(startMondayR.value + 'T00:00:00')
  const at = new Date(iso + 'T00:00:00')
  if (at < from) return -1
  const m = (at.getFullYear() - from.getFullYear()) * 12 + (at.getMonth() - from.getMonth())
  return Math.min(m, horizonR.value)
}

function monthStartIso(monthIdx) {
  if (monthIdx < 0 || monthIdx >= months.value.length) return null
  const [y, m] = months.value[monthIdx].split('-').map(Number)
  let d = new Date(y, m - 1, 1)
  const dow = (d.getDay() + 6) % 7 // 0 = понедельник
  d = new Date(d.getFullYear(), d.getMonth(), d.getDate() - dow)
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

function monthLeftPct(idx) {
  return (idx / horizonR.value) * 100
}

function handleTopPct(hours) {
  return 100 - (Math.min(hours, HOURS_MAX) / HOURS_MAX) * 100
}

function segWidthPct(i) {
  const cur = monthIndexFromDate(localCurve.value[i].weekStart)
  if (cur < 0) return 0
  const next = i + 1 < localCurve.value.length ? monthIndexFromDate(localCurve.value[i + 1].weekStart) : horizonR.value
  const end = next < 0 ? horizonR.value : next
  return ((end - cur) / horizonR.value) * 100
}

function clampHours(h) {
  return Math.max(0, Math.min(HOURS_MAX, h))
}

function fmtHours(v) {
  const n = Math.round(Number(v) * 100) / 100
  return `${n} ч/нед`
}

function planText() {
  const curve = localCurve.value
  if (!curve || curve.length === 0) return 'нет кривой'
  if (curve.length === 1) return `${fmtHours(curve[0].hours)}`
  const m1 = monthIndexFromDate(curve[1].weekStart)
  const transition = m1 > 0 ? `${m1 + 1}-го мес.` : '1-го мес.'
  return `${fmtHours(curve[0].hours)} → ${fmtHours(curve[1].hours)} с ${transition}`
}

// --- Загрузка геометрии (если не передана) + кривой -------------------------
async function loadCurve() {
  error.value = ''
  // 1. Разрешить startMonday / horizon, если не заданы пропсами.
  if (props.startMonday) {
    startMondayR.value = props.startMonday
    horizonR.value = props.horizonMonths
  } else {
    try {
      const headers = authHeaders()
      if (headers) {
        const res = await fetch(`${apiBase()}/planning/load-charts?horizonMonths=${props.horizonMonths}`, { headers })
        if (res.ok) {
          const c = await res.json()
          if (c.startMonday) startMondayR.value = c.startMonday
          if (c.horizonMonths) horizonR.value = c.horizonMonths
        }
      }
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e)
    }
  }
  // 2. Кривая.
  if (props.curve) {
    localCurve.value = [...props.curve]
    return
  }
  loading.value = true
  try {
    const headers = authHeaders()
    if (!headers) return
    const res = await fetch(`${apiBase()}/projects/${props.projectId}/load-curve`, { headers })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const data = await res.json()
    localCurve.value = Array.isArray(data) ? data : []
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

const writers = {}
function writerFor() {
  if (!writers[props.projectId]) {
    writers[props.projectId] = useLoadChartWrite(
      `${apiBase()}/projects/${props.projectId}/load-curve`,
      {},
      {
        localLabel: 'точка кривой',
        onError: (snapshot) => {
          if (!snapshot) return
          const i = localCurve.value.findIndex((c) => c.weekStart === snapshot.weekStart)
          if (i >= 0) localCurve.value[i].hours = Number(snapshot.hours)
        },
      }
    )
  }
  return writers[props.projectId]
}

// --- Перетаскивание ручки -----------------------------------------------------
let drag = null
function onHandlePointerDown(e, idx) {
  if (e.target !== e.currentTarget) return
  if (idx === 0) return // первая точка по горизонтали не двигается (только часы вниз)
  e.preventDefault()
  const lane = e.currentTarget.closest('.lane-track')
  drag = { index: idx, rect: lane.getBoundingClientRect(), moved: false }
  e.currentTarget.setPointerCapture?.(e.pointerId)
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
}
function onPointerMove(e) {
  if (!drag) return
  drag.moved = true
  const r = drag.rect
  const xPct = Math.max(0, Math.min(100, ((e.clientX - r.left) / r.width) * 100))
  const monthIdx = Math.round((xPct / 100) * horizonR.value)
  const yPct = Math.max(0, Math.min(100, ((r.bottom - e.clientY) / r.height) * 100))
  const hours = clampHours(Math.round((yPct / 100) * HOURS_MAX))
  const pt = localCurve.value[drag.index]
  pt.hours = hours
  pt.weekStart = monthStartIso(Math.max(0, Math.min(horizonR.value, monthIdx)))
}
function onPointerUp() {
  if (!drag) return
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
  const { index } = drag
  drag = null
  const pt = localCurve.value[index]
  writerFor().commit({ weekStart: pt.weekStart, hours: String(pt.hours) })
}

// --- Клик по пустому месту дорожки: добавить точку --------------------------
function onLaneClick(e) {
  if (e.target !== e.currentTarget) return
  const r = e.currentTarget.getBoundingClientRect()
  const xPct = Math.max(0, Math.min(100, ((e.clientX - r.left) / r.width) * 100))
  const monthIdx = Math.round((xPct / 100) * horizonR.value)
  if (monthIdx <= 0) return // первая точка уже привязана к началу горизонта
  const yPct = Math.max(0, Math.min(100, ((r.bottom - e.clientY) / r.height) * 100))
  const hours = clampHours(Math.round((yPct / 100) * HOURS_MAX))
  const weekStart = monthStartIso(monthIdx)
  localCurve.value.push({ weekStart, hours: Number(hours) })
  localCurve.value.sort((a, b) => new Date(a.weekStart) - new Date(b.weekStart))
  writerFor().commit({ weekStart, hours: String(hours) })
}

// --- Двойной клик по ручке: удалить точку (кроме первой) -------------------
function onHandleDblClick(e, idx) {
  e.preventDefault()
  if (idx === 0) return
  const pt = localCurve.value[idx]
  const hadId = pt.id != null
  localCurve.value.splice(idx, 1)
  if (hadId) {
    writerFor().remove(`${apiBase()}/projects/${props.projectId}/load-curve/${pt.id}`, {
      restore: () => localCurve.value.splice(idx, 0, pt),
    })
  } else {
    writerFor().cancel()
  }
}

// --- Клавиатура -------------------------------------------------------------
function onHandleKeydown(e, idx) {
  const pt = localCurve.value[idx]
  let handled = true
  let newMonthIdx = monthIndexFromDate(pt.weekStart)
  let hours = pt.hours
  if (e.key === 'ArrowLeft') newMonthIdx = Math.max(0, newMonthIdx - 1)
  else if (e.key === 'ArrowRight') newMonthIdx = Math.min(horizonR.value, newMonthIdx + 1)
  else if (e.key === 'ArrowUp') hours = clampHours(hours + 1)
  else if (e.key === 'ArrowDown') hours = clampHours(hours - 1)
  else if (e.key === 'Delete' || e.key === 'Backspace') {
    onHandleDblClick(e, idx)
    return
  } else {
    handled = false
  }
  if (!handled) return
  e.preventDefault()
  if (idx !== 0) pt.weekStart = monthStartIso(newMonthIdx)
  pt.hours = hours
  writerFor().commit({ weekStart: pt.weekStart, hours: String(pt.hours) })
}

// --- Мобильная текстовая форма ---------------------------------------------
function onMobileHours(e, idx) {
  localCurve.value[idx].hours = clampHours(Number(e.target.value) || 0)
  const pt = localCurve.value[idx]
  writerFor().commit({ weekStart: pt.weekStart, hours: String(pt.hours) })
}
function onMobileRemove(idx) {
  onHandleDblClick({ preventDefault() {} }, idx)
}

onMounted(loadCurve)
defineExpose({ reload: loadCurve })
</script>

<template>
  <div class="curve-lane">
    <div v-if="loading" class="lane-loading">Загрузка кривой…</div>
    <template v-else>
      <div class="lane-row">
        <div class="lane-header">
          <div class="lane-title">{{ title }}</div>
          <div class="lane-subtitle">{{ fmtHours(0) }}</div>
        </div>

        <div
          class="lane-track"
          role="img"
          :aria-label="`Кривая нагрузки ${title}: ${planText()}`"
          @click="onLaneClick"
        >
          <div
            v-for="(seg, i) in localCurve"
            :key="'seg-' + i"
            class="lane-segment"
            :class="{ zero: seg.hours === 0 }"
            :style="{
              left: monthLeftPct(monthIndexFromDate(seg.weekStart)) + '%',
              width: segWidthPct(i) + '%',
            }"
          ></div>

          <button
            v-for="(pt, i) in localCurve"
            :key="'h-' + i"
            type="button"
            class="lane-handle"
            :class="{ first: i === 0 }"
            :style="{
              left: monthLeftPct(monthIndexFromDate(pt.weekStart)) + '%',
              top: handleTopPct(pt.hours) + '%',
            }"
            :tabindex="0"
            :aria-label="`Точка ${i + 1}: с ${months[monthIndexFromDate(pt.weekStart)] || ''} — ${fmtHours(pt.hours)}`"
            @pointerdown="onHandlePointerDown($event, i)"
            @dblclick="onHandleDblClick($event, i)"
            @keydown="onHandleKeydown($event, i)"
          ></button>
        </div>

        <div class="lane-finish">
          <span class="plan-note">{{ planText() }}</span>
        </div>
      </div>

      <!-- Текстовая форма точек (на десктопе скрыта, на мобильном — показана). -->
      <div class="lane-mobile-form">
        <div v-for="(pt, i) in localCurve" :key="'m-' + i" class="mobile-point">
          <label>
            с {{ months[monthIndexFromDate(pt.weekStart)] || '…' }}
            <input
              type="number"
              min="0"
              max="20"
              step="1"
              :value="pt.hours"
              @change="onMobileHours($event, i)"
            />
            ч/нед
          </label>
          <button
            v-if="i !== 0"
            type="button"
            class="mobile-remove"
            @click="onMobileRemove(i)"
          >убрать</button>
        </div>
      </div>
    </template>

    <p v-if="error" class="lane-error">{{ error }}</p>
  </div>
</template>

<style scoped>
.curve-lane { width: 100%; }

.lane-loading { color: var(--wolf-muted); font-size: 12px; padding: 0.5rem 0; }

/* Одна Grid-координатная система (заголовок слева, дорожка, финиш справа). */
.lane-row {
  display: grid;
  grid-template-columns: 14rem minmax(0, 1fr) 9rem;
  align-items: center;
  column-gap: 1rem;
}

.lane-track {
  position: relative;
  height: 3.25rem;
  border-bottom: 1px solid var(--wolf-subrule);
  cursor: crosshair;
  background: transparent;
}
.lane-segment {
  position: absolute;
  top: 0;
  bottom: 0;
  background: var(--wolf-subrule);
  border-top: 1.5px solid var(--wolf-ink);
  pointer-events: none;
}
.lane-segment.zero { background: transparent; border-top-color: transparent; }

.lane-handle {
  position: absolute;
  width: 9px;
  height: 9px;
  margin: -4.5px 0 0 -4.5px;
  padding: 0;
  background: var(--wolf-ink);
  border: 0;
  border-radius: 0;
  cursor: grab;
  pointer-events: auto;
}
.lane-handle.first { cursor: ns-resize; }
.lane-handle:active { background: var(--wolf-focus); cursor: grabbing; }

.lane-header { padding: 0 0.5rem 0 0; }
.lane-title { font-size: 13px; color: var(--wolf-ink); font-weight: 600; }
.lane-subtitle { font-size: 11px; color: var(--wolf-muted); }

.lane-finish {
  padding: 0 0 0 0.5rem;
  font-size: 11px;
  color: var(--wolf-muted);
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.plan-note { font-variant-numeric: tabular-nums; }

.lane-mobile-form { display: none; }
.lane-error {
  margin: 0.4rem 0 0;
  font-size: 0.72rem;
  color: var(--wolf-ink);
  border-bottom: 1px solid var(--wolf-ink);
}

/* Мобильный вид (≤700px): дорожка read-only, текстовая форма точек. */
@media (max-width: 700px) {
  .lane-handle { display: none; }
  .lane-track { cursor: default; }
  .lane-mobile-form {
    display: block;
    grid-column: 1 / -1;
    margin: 0.4rem 0 0.8rem;
    padding-left: 14rem;
  }
  .mobile-point {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    font-size: 12px;
    color: var(--wolf-muted);
    padding: 2px 0;
  }
  .mobile-point input { width: 4rem; }
  .mobile-remove {
    border: 0;
    border-bottom: 1px solid var(--wolf-ink);
    background: transparent;
    color: var(--wolf-ink);
    cursor: pointer;
    font-size: 12px;
  }
}
</style>
