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
  along with this program. If not see <https://www.gnu.org/licenses/>.
-->
<script setup>
/*
 * Вкладка «Кривые» (release 0.8, тикет 03, вариант 3a).
 *
 * Дорожка на проект со ступенчатой кривой часов в неделю на горизонте 18 месяцев.
 * Перетаскивание ручки меняет часы (вертикаль, шаг 1, диапазон 0–20) и месяц перехода
 * (горизонталь). Клик по пустому месту дорожки добавляет точку, двойной клик по точке
 * её убирает (кроме первой). Вертикальная риска — прогнозный финиш по накоплению часов.
 * Снизу — суммарная загрузка по месяцам против недельной нормы.
 *
 * Контракт тикета 02:
 *   - данные приходят из inject('loadCharts').data (GET /planning/load-charts);
 *   - запись через makeWriter(url, body, opts).commit(value) (дебаунс 400 мс + откат при ошибке PUT);
 *   - удаление точки через makeWriter(...).remove(deleteUrl, snapshot).
 *
 * Запись на сервер: PUT /projects/{id}/load-curve — upsert одной точки по weekStart
 * (см. LoadCurveService.save). Поэтому drag/добавление/часы шлют PUT одной точки,
 * удаление — DELETE /projects/{id}/load-curve/{entryId}.
 */
import { computed, inject, ref, watch } from 'vue'
import { apiBase } from '../api'

const { data, makeWriter } = inject('loadCharts')

// --- Константы геометрии ----------------------------------------------------
const HOURS_MAX = 20 // верхняя граница шкалы часов (диапазон 0–20)

// --- Производные из данных --------------------------------------------------
const horizonMonths = computed(() => data.value?.horizonMonths ?? 18)
const startMonday = computed(() => data.value?.startMonday ?? null)
const projects = computed(() => data.value?.projects ?? [])
const monthlyLoad = computed(() => data.value?.monthlyLoad ?? [])
const weeklyLimit = computed(() => data.value?.weeklyLimit ?? 0)

// Позиция пунктирной линии нормы в строке суммарной загрузки (сверху вниз, %).
const limitTopPct = computed(() => {
  const h = weeklyLimit.value
  return Math.max(0, Math.min(100, (h / HOURS_MAX) * 100))
})

// Текстовая запись перебора. Без сигнального цвета (тихий контракт 0.3).
const overloadText = computed(() => {
  let peak = 0
  for (const m of monthlyLoad.value) peak = Math.max(peak, m.hours ?? 0)
  const limit = weeklyLimit.value
  if (peak > limit) {
    const over = Math.round((peak - limit) * 100) / 100
    return `Пик загрузки ${fmtHours(peak)} — перебор на ${fmtHours(over)}`
  }
  return `Пик загрузки ${fmtHours(peak)} из ${fmtHours(limit)}. В лимите.`
})

// --- Утилиты дат и форматирования ------------------------------------------
// Месяцы горизонта: список «YYYY-MM» от startMonday (понедельник первой недели).
const months = computed(() => {
  if (!startMonday.value) return []
  const out = []
  const d = new Date(startMonday.value + 'T00:00:00')
  for (let i = 0; i < horizonMonths.value; i++) {
    out.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`)
    d.setMonth(d.getMonth() + 1)
  }
  return out
})

// Индекс месяца (0-based) для ISO-даты: разница в месяцах от startMonday.
function monthIndexFromDate(iso) {
  if (!iso || !startMonday.value) return -1
  const from = new Date(startMonday.value + 'T00:00:00')
  const at = new Date(iso + 'T00:00:00')
  if (at < from) return -1
  const m = (at.getFullYear() - from.getFullYear()) * 12 + (at.getMonth() - from.getMonth())
  return Math.min(m, horizonMonths.value)
}

// Понедельник недели, содержащей 1-е число месяца monthIdx (чтобы weekStart попал в месяц).
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

// Позиция месяца (левый край) в % от ширины дорожки.
function monthLeftPct(idx) {
  return (idx / horizonMonths.value) * 100
}

// Высота ручки по часам (в % от верха дорожки).
function handleTopPct(hours) {
  return 100 - (Math.min(hours, HOURS_MAX) / HOURS_MAX) * 100
}

// Ширина сегмента i: от его месяца до месяца следующей точки (или конца горизонта).
function segWidthPct(p, i) {
  const curve = curveOf(p)
  const cur = monthIndexFromDate(curve[i].weekStart)
  if (cur < 0) return 0
  const next = i + 1 < curve.length ? monthIndexFromDate(curve[i + 1].weekStart) : horizonMonths.value
  const end = next < 0 ? horizonMonths.value : next
  return ((end - cur) / horizonMonths.value) * 100
}

// Позиция риски финиша по кривой (дата → индекс месяца → %).
function finishLeftPct(p) {
  if (!p.forecastByCurve) return -1
  return monthLeftPct(monthIndexFromDate(p.forecastByCurve))
}

function totalBarPct(i) {
  const h = monthlyLoad.value[i]?.hours ?? 0
  return Math.max(0, Math.min(100, (h / HOURS_MAX) * 100))
}

function isOverLimit(i) {
  return !!monthlyLoad.value[i]?.overLimit
}

function fmtHours(v) {
  const n = Math.round(Number(v) * 100) / 100
  return `${n} ч/нед`
}

function fmtDate(iso) {
  if (!iso) return 'не закрывается'
  const d = new Date(iso + 'T00:00:00')
  return d.toLocaleDateString('ru-RU', { day: 'numeric', month: 'long', year: 'numeric' })
}

// Текстовая запись плана: «16 ч/нед → 8 ч с 2-го мес.».
function planText(p) {
  const curve = curveOf(p)
  if (!curve || curve.length === 0) return 'нет кривой'
  if (curve.length === 1) return `${fmtHours(curve[0].hours)}`
  const m1 = monthIndexFromDate(curve[1].weekStart)
  const transition = m1 > 0 ? `${m1 + 1}-го мес.` : '1-го мес.'
  return `${fmtHours(curve[0].hours)} → ${fmtHours(curve[1].hours)} с ${transition}`
}

// --- Локальное (оптимистичное) зеркало кривых -----------------------------
// Реактивная копия curve по projectId: точки двигаются сразу, без ожидания сервера.
const localCurves = ref({})
function rebuildLocal() {
  const next = {}
  for (const p of projects.value) next[p.id] = [...(p.curve ?? [])]
  localCurves.value = next
}
watch(() => data.value, rebuildLocal, { immediate: true })

function curveOf(p) {
  if (!localCurves.value[p.id]) localCurves.value[p.id] = [...(p.curve ?? [])]
  return localCurves.value[p.id]
}

function clampHours(h) {
  return Math.max(0, Math.min(HOURS_MAX, h))
}

// --- Общий хелпер записи на проект (provide из тикета 02) ------------------
const writers = {}
function writerFor(p) {
  if (!writers[p.id]) {
    writers[p.id] = makeWriter(`${apiBase()}/projects/${p.id}/load-curve`, {}, {
      localLabel: 'точка кривой',
      onError: (snapshot) => {
        if (!snapshot) return
        const curve = localCurves.value[p.id]
        if (!curve) return
        if (snapshot.restore) {
          snapshot.restore()
        } else if (snapshot.weekStart) {
          const i = curve.findIndex((c) => c.weekStart === snapshot.weekStart)
          if (i >= 0) curve[i].hours = Number(snapshot.hours)
        }
      },
    })
  }
  return writers[p.id]
}

// --- Перетаскивание ручки ---------------------------------------------------
let drag = null // { projectId, index, rect, moved }

function onHandlePointerDown(e, p, idx) {
  if (e.target !== e.currentTarget) return
  if (idx === 0) return // первая точка по горизонтали не двигается (только часы вниз)
  e.preventDefault()
  const lane = e.currentTarget.closest('.lane-track')
  drag = { projectId: p.id, index: idx, rect: lane.getBoundingClientRect(), moved: false }
  e.currentTarget.setPointerCapture?.(e.pointerId)
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
}

function onPointerMove(e) {
  if (!drag) return
  drag.moved = true
  const r = drag.rect
  const xPct = Math.max(0, Math.min(100, ((e.clientX - r.left) / r.width) * 100))
  const monthIdx = Math.round((xPct / 100) * horizonMonths.value)
  const yPct = Math.max(0, Math.min(100, ((r.bottom - e.clientY) / r.height) * 100))
  const hours = clampHours(Math.round((yPct / 100) * HOURS_MAX))
  const curve = localCurves.value[drag.projectId]
  const pt = curve[drag.index]
  const newMonthIdx = Math.max(0, Math.min(horizonMonths.value, monthIdx))
  pt.hours = hours
  pt.weekStart = monthStartIso(newMonthIdx)
}

function onPointerUp() {
  if (!drag) return
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
  const { projectId, index } = drag
  drag = null
  const curve = localCurves.value[projectId]
  const pt = curve[index]
  writerFor({ id: projectId }).commit({ weekStart: pt.weekStart, hours: String(pt.hours) })
}

// --- Клик по пустому месту дорожки: добавить точку -------------------------
function onLaneClick(e, p) {
  if (e.target !== e.currentTarget) return // клик по ручке/сегменту не добавляет
  const r = e.currentTarget.getBoundingClientRect()
  const xPct = Math.max(0, Math.min(100, ((e.clientX - r.left) / r.width) * 100))
  const monthIdx = Math.round((xPct / 100) * horizonMonths.value)
  if (monthIdx <= 0) return // первая точка уже привязана к началу горизонта
  const yPct = Math.max(0, Math.min(100, ((r.bottom - e.clientY) / r.height) * 100))
  const hours = clampHours(Math.round((yPct / 100) * HOURS_MAX))
  const curve = curveOf(p)
  const weekStart = monthStartIso(monthIdx)
  curve.push({ weekStart, hours: Number(hours) })
  curve.sort((a, b) => new Date(a.weekStart) - new Date(b.weekStart))
  writerFor(p).commit({ weekStart, hours: String(hours) })
}

// --- Двойной клик по ручке: удалить точку (кроме первой) -------------------
function onHandleDblClick(e, p, idx) {
  e.preventDefault()
  if (idx === 0) return // первая точка не удаляется
  const curve = localCurves.value[p.id]
  const pt = curve[idx]
  const hadId = pt.id != null
  curve.splice(idx, 1)
  if (hadId) {
    const deleteUrl = `${apiBase()}/projects/${p.id}/load-curve/${pt.id}`
    writerFor(p).remove(deleteUrl, { restore: () => curve.splice(idx, 0, pt) })
  }
  // Без id (только что добавленная, ещё не подтверждённая сервером) — локально убрали,
  // серверную запись отменяем, чтобы не создавать осиротевшую точку.
  else writerFor(p).cancel()
}

// --- Клавиатура по ручке ----------------------------------------------------
function onHandleKeydown(e, p, idx) {
  const curve = localCurves.value[p.id]
  const pt = curve[idx]
  let handled = true
  let newMonthIdx = monthIndexFromDate(pt.weekStart)
  let hours = pt.hours
  if (e.key === 'ArrowLeft') newMonthIdx = Math.max(0, newMonthIdx - 1)
  else if (e.key === 'ArrowRight') newMonthIdx = Math.min(horizonMonths.value, newMonthIdx + 1)
  else if (e.key === 'ArrowUp') hours = clampHours(hours + 1)
  else if (e.key === 'ArrowDown') hours = clampHours(hours - 1)
  else if (e.key === 'Delete' || e.key === 'Backspace') {
    onHandleDblClick(e, p, idx)
    return
  } else {
    handled = false
  }
  if (!handled) return
  e.preventDefault()
  if (idx !== 0) pt.weekStart = monthStartIso(newMonthIdx)
  pt.hours = hours
  writerFor(p).commit({ weekStart: pt.weekStart, hours: String(hours) })
}

// --- Мобильная текстовая форма (≤700px) ------------------------------------
function onMobileHours(e, p, idx) {
  const curve = localCurves.value[p.id]
  const pt = curve[idx]
  pt.hours = clampHours(Number(e.target.value) || 0)
  writerFor(p).commit({ weekStart: pt.weekStart, hours: String(pt.hours) })
}

function onMobileRemove(p, idx) {
  onHandleDblClick({ preventDefault() {} }, p, idx)
}
</script>

<template>
  <div class="curves-tab">
    <!-- Шкала месяцев: одна Grid-координатная система (заголовок). -->
    <div class="load-lane-header" aria-hidden="true">
      <div class="lane-title-corner"></div>
      <div class="lane-month-scale">
        <span
          v-for="(m, i) in months"
          :key="m"
          class="month-label"
          :class="{ quarter: i % 3 === 0 }"
          :style="{ left: monthLeftPct(i) + '%' }"
        >{{ m }}</span>
      </div>
      <div class="lane-finish-corner"></div>
    </div>

    <!-- Дорожки по проектам. -->
    <div class="load-lanes">
      <div v-for="p in projects" :key="p.id" class="lane-row">
        <div class="lane-header">
          <div class="lane-title">{{ p.title }}</div>
          <div class="lane-subtitle">
            {{ p.deloCount }} Дел · {{ fmtHours(p.effortHours) }}
            <span v-if="p.effortSource === 'ESTIMATED'" class="est-flag">оценка</span>
          </div>
        </div>

        <div
          class="lane-track"
          role="img"
          :aria-label="`Кривая нагрузки ${p.title}: ${planText(p)}`"
          @click="onLaneClick($event, p)"
        >
          <!-- Сегменты кривой -->
          <div
            v-for="(seg, i) in curveOf(p)"
            :key="'seg-' + i"
            class="lane-segment"
            :class="{ zero: seg.hours === 0 }"
            :style="{
              left: monthLeftPct(monthIndexFromDate(seg.weekStart)) + '%',
              width: segWidthPct(p, i) + '%',
            }"
          ></div>

          <!-- Риска прогнозного финиша по кривой -->
          <div
            v-if="p.forecastByCurve && finishLeftPct(p) >= 0"
            class="finish-mark"
            :style="{ left: finishLeftPct(p) + '%' }"
          ></div>

          <!-- Ручки (на мобильном скрыты через CSS — дорожка read-only) -->
          <button
            v-for="(pt, i) in curveOf(p)"
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
            @pointerdown="onHandlePointerDown($event, p, i)"
            @dblclick="onHandleDblClick($event, p, i)"
            @keydown="onHandleKeydown($event, p, i)"
          ></button>
        </div>

        <div class="lane-finish">
          <span v-if="p.forecastByCurve" class="finish-date">{{ fmtDate(p.forecastByCurve) }}</span>
          <span v-else class="finish-none">не закрывается</span>
          <span class="plan-note">{{ planText(p) }}</span>
        </div>

        <!-- Мобильная текстовая форма редактирования точек (≤700px). -->
        <div class="lane-mobile-form">
          <div v-for="(pt, i) in curveOf(p)" :key="'m-' + i" class="mobile-point">
            <label>
              с {{ months[monthIndexFromDate(pt.weekStart)] || '…' }}
              <input
                type="number"
                min="0"
                max="20"
                step="1"
                :value="pt.hours"
                @change="onMobileHours($event, p, i)"
              />
              ч/нед
            </label>
            <button
              v-if="i !== 0"
              type="button"
              class="mobile-remove"
              @click="onMobileRemove(p, i)"
            >убрать</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Строка суммарной загрузки по месяцам. -->
    <div class="load-total-row" :style="{ '--limit-top': limitTopPct + '%' }">
      <div class="total-title">
        Суммарная нагрузка
        <div class="total-note">пунктир — {{ fmtHours(weeklyLimit) }} / нед</div>
      </div>
      <div class="total-bars">
        <div
          v-for="(m, i) in months"
          :key="m"
          class="total-col"
          :class="{ over: isOverLimit(i) }"
        >
          <div class="total-bar" :style="{ height: totalBarPct(i) + '%' }"></div>
          <span class="total-val">{{ Math.round((monthlyLoad[i]?.hours ?? 0) * 10) / 10 }}</span>
        </div>
        <div class="total-limit"></div>
      </div>
      <div class="total-finish"></div>
    </div>

    <p class="overload-statement">{{ overloadText }}</p>
  </div>
</template>

<style scoped>
.curves-tab { width: 100%; }

/* Одна Grid-координатная система для шкалы, дорожек и суммарной строки. */
.load-lane-header,
.lane-row,
.load-total-row {
  display: grid;
  grid-template-columns: 14rem minmax(0, 1fr) 9rem;
  align-items: stretch;
}

.lane-month-scale {
  position: relative;
  height: 1.25rem;
  border-bottom: 1px solid var(--wolf-rule);
}
.month-label {
  position: absolute;
  top: 0;
  transform: translateX(2px);
  font-size: 10px;
  color: var(--wolf-faint);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.month-label.quarter { color: var(--wolf-muted); }

/* Дорожка */
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

.finish-mark {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 1px;
  background: var(--wolf-muted);
  pointer-events: none;
}

/* Заголовок дорожки (слева) */
.lane-header { padding: 0 0.5rem 0 0; }
.lane-title { font-size: 13px; color: var(--wolf-ink); font-weight: 600; }
.lane-subtitle { font-size: 11px; color: var(--wolf-muted); font-variant-numeric: tabular-nums; }
.est-flag { font-style: italic; }

/* Финиш (справа) */
.lane-finish {
  padding: 0 0 0 0.5rem;
  font-size: 11px;
  color: var(--wolf-muted);
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.finish-date { color: var(--wolf-ink); font-variant-numeric: tabular-nums; }
.plan-note { font-variant-numeric: tabular-nums; }

/* Мобильная текстовая форма — скрыта на десктопе. */
.lane-mobile-form { display: none; }

/* Суммарная строка */
.total-bars {
  position: relative;
  height: 4rem;
  display: flex;
  align-items: flex-end;
  gap: 0;
  border-bottom: 1px solid var(--wolf-rule);
}
.total-col {
  flex: 1 1 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  align-items: center;
  position: relative;
}
.total-bar {
  width: 70%;
  background: var(--wolf-subrule);
  border-top: 1px solid var(--wolf-ink);
}
/* Перебор — штриховка, не красный цвет (тихий контракт 0.3). */
.total-col.over .total-bar {
  background: repeating-linear-gradient(
    45deg,
    var(--wolf-subrule),
    var(--wolf-subrule) 2px,
    var(--wolf-faint) 2px,
    var(--wolf-faint) 4px
  );
}
.total-val { font-size: 9px; color: var(--wolf-faint); font-variant-numeric: tabular-nums; }
.total-limit {
  position: absolute;
  left: 0;
  right: 0;
  top: var(--limit-top, 0);
  border-top: 1px dashed var(--wolf-faint);
  pointer-events: none;
}
.total-title { font-size: 11px; color: var(--wolf-muted); }
.total-note { font-size: 10px; color: var(--wolf-faint); }
.total-finish { }

.overload-statement {
  margin: 0.5rem 0 0;
  font-size: 12px;
  color: var(--wolf-ink);
  font-variant-numeric: tabular-nums;
}

/* Мобильный вид (≤700px): дорожка read-only, ручки скрыты, текстовая форма точек. */
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
