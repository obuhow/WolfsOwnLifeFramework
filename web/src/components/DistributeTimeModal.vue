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
 * Окно «Распределить время» (release 1.1, тикет 04).
 *
 * Четвёртый, ручной способ распределения Плана-на-неделю — дополняет стратегии
 * Бюджет/Лестница/Очередь (реш. D владельца), не заменяет их. Суммарные плановые
 * часы проекта (totalPlanHours) раскладываются по ISO-неделям диапазона дат ровной
 * прямой; клик по неделе делит кривую на 2 сегмента; перетаскивание сегмента меняет
 * его часы, остальные пересчитываются так, что Σ часов проекта НЕ меняется
 * (математика — в distributeTime.js, покрыта юнит-тестами).
 *
 * Сохранение — в «План на неделю» проекта (PUT /gantt/week-plans, тот же эндпоинт,
 * что правит недельные планы на Гантте): по одному писателю на неделю через общий
 * write-helper (inject('loadCharts').makeWriter) — дебаунс 400 мс, откат к серверному
 * значению при ошибке PUT, строка ошибки под кривой.
 *
 * Тихий контракт 0.3: только токены --wolf-*, без red/#a33/--wolf-danger,
 * без role="alert", без прогресс-баров. Перегруз кривой — нейтрально (текст).
 */
import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { apiBase } from '../api'
import {
  adjustSegment,
  buildWeeks,
  round2,
  splitAt,
  straightLine,
  totalHours,
} from '../distributeTime'

const props = defineProps({
  // Проект из load-charts (id, title) — для записи и заголовка.
  project: { type: Object, required: true },
  // Детали из GET /projects/{id}: startDate, endDate, totalPlanHours, title.
  detail: { type: Object, required: true },
})
const emit = defineEmits(['close'])

const { makeWriter } = inject('loadCharts')
const backdropRef = ref(null)

const startDate = ref(props.detail?.startDate || '')
const endDate = ref(props.detail?.endDate || '')
const weeks = ref([])
const splitIndex = ref(null)
const saveError = ref('')

const totalPlan = computed(() => {
  const v = Number(props.detail?.totalPlanHours)
  return Number.isFinite(v) && v > 0 ? v : 0
})

const dateError = computed(() => {
  if (startDate.value && endDate.value && endDate.value < startDate.value) {
    return 'Дата завершения раньше даты начала'
  }
  return ''
})

// Недели диапазона — прямая по totalPlanHours. Пересбор при смене дат.
function rebuild() {
  saveError.value = ''
  if (dateError.value) {
    weeks.value = []
    splitIndex.value = null
    return
  }
  const base = buildWeeks(startDate.value, endDate.value)
  weeks.value = straightLine(totalPlan.value, base)
  splitIndex.value = null
  snapshot()
}
// Ввод дат пользователем = новое распределение → прямая сразу пишется.
// При открытии окна (onMounted) запись НЕ выполняется — только предзаполнение.
watch([startDate, endDate], () => {
  rebuild()
  commitAll()
})

// Снапшот «серверных» часов для отката при ошибке PUT (значения до жеста =
// значения после последнего успешного сохранения).
const serverSnapshot = ref(new Map())
function snapshot() {
  serverSnapshot.value = new Map(weeks.value.map((w) => [weekKey(w), w.hours]))
}
function weekKey(w) {
  return `${w.isoYear}|${w.isoWeek}`
}

// --- Запись: по одному write-helper на неделю (debounce 400 мс, откат) ------
const writers = new Map()
function onPutError(body) {
  // Откат конкретной недели к серверному значению (до жеста).
  const key = `${body.isoYear}|${body.isoWeek}`
  const wk = weeks.value.find((w) => weekKey(w) === key)
  if (wk && serverSnapshot.value.has(key)) wk.hours = serverSnapshot.value.get(key)
}
// Первая ошибка среди писателей — строкой под кривой (тихий контракт).
watch(
  () => [...writers.values()].map((w) => w.error.value),
  (errs) => {
    const first = errs.find(Boolean)
    saveError.value = first || ''
  }
)
function writerFor(w) {
  const key = weekKey(w)
  let wr = writers.get(key)
  if (!wr) {
    wr = makeWriter(
      `${apiBase()}/gantt/week-plans`,
      { projectId: props.project.id, isoYear: w.isoYear, isoWeek: w.isoWeek, planHours: 0 },
      { localLabel: 'недельные часы', onError: onPutError }
    )
    writers.set(key, wr)
  }
  return wr
}
function commitAll() {
  if (!weeks.value.length) return
  for (const w of weeks.value) {
    const wr = writerFor(w)
    wr.error.value = ''
    wr.commit({
      projectId: props.project.id,
      isoYear: w.isoYear,
      isoWeek: w.isoWeek,
      planHours: round2(w.hours),
    })
  }
}
function flushAll() {
  for (const wr of writers.values()) wr.flush()
}

// --- Взаимодействие ---------------------------------------------------------
const scaleMax = computed(() => {
  const mx = weeks.value.reduce((m, w) => Math.max(m, Number(w.hours) || 0), 0)
  return Math.max(8, Math.ceil(mx * 1.15))
})
const segCount = computed(() => (splitIndex.value != null ? 2 : 1))
const segSummary = computed(() => {
  if (!weeks.value.length) return ''
  const k = weeks.value.length
  const total = totalHours(weeks.value)
  const base = `недель: ${k} · всего ${round2(total)} ч`
  if (splitIndex.value == null) return base
  const aCount = splitIndex.value + 1
  const hA = weeks.value.slice(0, aCount).reduce((s, w) => s + w.hours, 0) / aCount
  const hB = weeks.value.slice(aCount).reduce((s, w) => s + w.hours, 0) / (k - aCount)
  return `${base} · сегменты: ${round2(hA)} → ${round2(hB)} ч/нед`
})

function barHeightPct(h) {
  return Math.max(0, Math.min(100, (Number(h) / scaleMax.value) * 100))
}
function segOf(i) {
  return splitIndex.value != null && i > splitIndex.value ? 1 : 0
}
function isSplitBar(i) {
  return splitIndex.value != null && i === splitIndex.value
}
function fmtHours(v) {
  return `${round2(Number(v))} ч`
}

// Клик по неделе: поставить/сдвинуть/снять точку разбивки (не на краях).
function onBarClick(e, i) {
  if (dragMoved) return // это был жест перетаскивания, не клик
  const k = weeks.value.length
  if (i <= 0 || i >= k - 1) return
  const res = splitAt(weeks.value, splitIndex.value === i ? null : i)
  weeks.value = res.weeks
  splitIndex.value = res.splitIndex
  snapshot()
  commitAll()
}

// Перетаскивание сегмента (только после разбивки — до неё Σ менять нечем).
let drag = null
let dragMoved = false
function onBarPointerDown(e, i) {
  if (e.target !== e.currentTarget) return
  if (splitIndex.value == null) return // до разбивки — только клик
  e.preventDefault()
  const track = e.currentTarget.closest('.dt-track')
  drag = {
    seg: segOf(i),
    rect: track.getBoundingClientRect(),
    startX: e.clientX,
    startY: e.clientY,
  }
  dragMoved = false
  e.currentTarget.setPointerCapture?.(e.pointerId)
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
}
function onPointerMove(e) {
  if (!drag) return
  if (!dragMoved && (Math.abs(e.clientX - drag.startX) > 3 || Math.abs(e.clientY - drag.startY) > 3)) {
    dragMoved = true
    snapshot() // значения до жеста — для отката
  }
  if (!dragMoved) return
  const r = drag.rect
  const yPct = Math.max(0, Math.min(100, ((r.bottom - e.clientY) / r.height) * 100))
  const target = (yPct / 100) * scaleMax.value
  const res = adjustSegment(weeks.value, splitIndex.value, drag.seg, target)
  weeks.value = res.weeks
  commitAll()
}
function onPointerUp() {
  if (!drag) return
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
  drag = null
}

// Клавиатура: ↑/↓ меняют высоту сегмента на 0.25 ч (Enter/Space — нативный клик кнопки).
function onBarKeydown(e, i) {
  if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') return
  if (splitIndex.value == null) return // до разбивки высоту не меняем
  const seg = segOf(i)
  const dir = e.key === 'ArrowUp' ? 1 : -1
  const aCount = splitIndex.value + 1
  const cur = seg === 0
    ? weeks.value.slice(0, aCount).reduce((s, w) => s + w.hours, 0) / aCount
    : weeks.value.slice(aCount).reduce((s, w) => s + w.hours, 0) / (weeks.value.length - aCount)
  e.preventDefault()
  snapshot()
  const res = adjustSegment(weeks.value, splitIndex.value, seg, cur + 0.25 * dir)
  weeks.value = res.weeks
  commitAll()
}

// Мобильная текстовая форма высот сегментов.
function onSegmentInput(seg, e) {
  const target = Math.max(0, Number(e.target.value) || 0)
  snapshot()
  const res = adjustSegment(weeks.value, splitIndex.value, seg, target)
  weeks.value = res.weeks
  e.target.value = round2(res.actual)
  commitAll()
}
function segmentHours(seg) {
  if (!weeks.value.length) return 0
  const k = weeks.value.length
  if (splitIndex.value == null) return totalHours(weeks.value) / k
  const aCount = splitIndex.value + 1
  const slice = seg === 0 ? weeks.value.slice(0, aCount) : weeks.value.slice(aCount)
  return slice.reduce((s, w) => s + w.hours, 0) / slice.length
}

function close() {
  flushAll() // слить отложенные записи перед закрытием
  emit('close')
}
function onKeydown(e) {
  if (e.key === 'Escape') close()
}

onMounted(() => {
  // Предзаполненные даты из деталей проекта — прямая сразу (без записи).
  rebuild()
  // Фокус на диалог: Esc и клавиатура ловятся сразу после открытия.
  backdropRef.value?.focus()
})
onBeforeUnmount(flushAll)
</script>

<template>
  <div
    ref="backdropRef"
    class="dt-backdrop"
    role="dialog"
    aria-modal="true"
    :aria-label="`Распределить время: ${project.title}`"
    tabindex="-1"
    @click.self="close"
    @keydown="onKeydown"
  >
    <section class="dt-dialog">
      <div class="dt-header">
        <div>
          <h2 id="dt-title">Распределить время</h2>
          <p class="dt-sub">
            {{ project.title }}
            <span class="dt-volume">· план {{ round2(totalPlan) }} ч</span>
          </p>
        </div>
        <button type="button" class="icon-btn" aria-label="Закрыть" @click="close">×</button>
      </div>

      <!-- Форма дат проекта (предзаполняется из startDate/endDate). -->
      <div class="dt-dates">
        <label class="dt-date">
          <span>Дата начала</span>
          <input v-model="startDate" type="date" aria-label="Дата начала проекта" />
        </label>
        <label class="dt-date">
          <span>Дата завершения</span>
          <input v-model="endDate" type="date" aria-label="Дата завершения проекта" />
        </label>
      </div>
      <p v-if="dateError" class="dt-note">{{ dateError }}</p>
      <p v-else-if="totalPlan === 0" class="dt-note">
        У проекта нет плановых часов — задайте «План, часы» в карточке проекта.
      </p>
      <p v-else-if="weeks.length === 0" class="dt-note">Задайте даты начала и завершения — по ним строится прямая.</p>

      <!-- Кривая: недели диапазона. Клик по неделе — точка разбивки на 2 сегмента. -->
      <template v-if="weeks.length > 0 && totalPlan > 0">
        <div
          class="dt-track"
          role="group"
          :aria-label="`Кривая распределения ${project.title}: ${segSummary}`"
        >
          <button
            v-for="(w, i) in weeks"
            :key="weekKey(w)"
            type="button"
            class="dt-bar"
            :class="{
              segB: segOf(i) === 1,
              split: isSplitBar(i),
              edge: i === 0 || i === weeks.length - 1,
            }"
            :style="{ height: barHeightPct(w.hours) + '%' }"
            :title="`W${w.isoWeek} (${w.weekStart}): ${fmtHours(w.hours)}`"
            :aria-label="`Неделя W${w.isoWeek}: ${fmtHours(w.hours)}. Клик — разбить кривую на 2 сегмента`"
            @click="onBarClick($event, i)"
            @pointerdown="onBarPointerDown($event, i)"
            @keydown="onBarKeydown($event, i)"
          >
            <span v-if="weeks.length <= 16" class="dt-bar-val">{{ round2(w.hours) }}</span>
          </button>
        </div>

        <!-- Шкала недель (подписи только у краёв и каждой 4-й — при длинном диапазоне). -->
        <div class="dt-week-scale" aria-hidden="true">
          <span
            v-for="(w, i) in weeks"
            :key="'s' + weekKey(w)"
            class="dt-week-label"
            :class="{ show: i % 4 === 0 || i === weeks.length - 1 }"
          >W{{ w.isoWeek }}</span>
        </div>

        <p class="dt-summary">{{ segSummary }}</p>
        <p class="dt-hint">
          Клик по неделе делит кривую на 2 сегмента; перетащите сегмент вверх или вниз —
          остальные пересчитаются так, что сумма часов проекта не меняется.
        </p>
      </template>

      <!-- Мобильная текстовая форма высот сегментов (≤700px). -->
      <div v-if="weeks.length > 0 && totalPlan > 0" class="dt-mobile-form">
        <label v-for="s in segCount" :key="'seg' + s" class="dt-seg-field">
          <span>Сегмент {{ s }} · ч/нед</span>
          <input
            type="number"
            min="0"
            step="0.25"
            :value="round2(segmentHours(s - 1))"
            @change="onSegmentInput(s - 1, $event)"
          />
        </label>
      </div>

      <p v-if="saveError" class="dt-error">{{ saveError }}</p>

      <div class="form-actions">
        <button type="button" class="btn btn-primary" @click="close">Готово</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* Тот же паттерн модалки, что в ProjectDetailView (тикет 06, 0.8). */
.dt-backdrop {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: grid;
  place-items: center;
  padding: 1rem;
  background: rgba(255, 255, 255, 0.82);
}

.dt-dialog {
  width: min(680px, calc(100vw - 2rem));
  max-height: calc(100vh - 2rem);
  overflow: auto;
  display: grid;
  gap: 0.85rem;
  padding: 1.5rem;
  background: var(--wolf-surface);
  border: 1px solid var(--wolf-ink);
}

.dt-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.dt-header h2 {
  margin: 0;
  font-size: 1.1rem;
  color: var(--wolf-ink);
  font-weight: 600;
}

.dt-sub {
  margin: 0.2rem 0 0;
  font-size: 0.82rem;
  color: var(--wolf-muted);
}

.dt-volume {
  font-variant-numeric: tabular-nums;
}

.icon-btn {
  border: 0;
  background: transparent;
  color: var(--wolf-muted);
  font-size: 1.3rem;
  line-height: 1;
  cursor: pointer;
}

.icon-btn:hover { color: var(--wolf-ink); }

/* Форма дат */
.dt-dates {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}

.dt-date {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.75rem;
  color: var(--wolf-muted);
}

.dt-date input {
  font: inherit;
  color: var(--wolf-ink);
  background: var(--wolf-surface);
  border: 1px solid var(--wolf-rule);
  padding: 0.35rem 0.5rem;
}

.dt-note {
  margin: 0;
  font-size: 0.8rem;
  color: var(--wolf-muted);
}

/* Кривая: столбики недель, высота = часы / scaleMax. */
.dt-track {
  position: relative;
  height: 10rem;
  display: flex;
  align-items: flex-end;
  gap: 1px;
  border-bottom: 1px solid var(--wolf-rule);
  cursor: crosshair;
}

.dt-bar {
  flex: 1 1 0;
  min-width: 0;
  padding: 0;
  border: 0;
  border-top: 1.5px solid var(--wolf-ink);
  background: var(--wolf-subrule);
  cursor: grab;
  position: relative;
}

/* Сегмент B — нейтральная штриховка той же гаммы (не сигнальный цвет). */
.dt-bar.segB {
  background: repeating-linear-gradient(
    45deg,
    var(--wolf-subrule),
    var(--wolf-subrule) 2px,
    var(--wolf-faint) 2px,
    var(--wolf-faint) 4px
  );
}

/* Неделя-точка разбивки — утолщённая граница. */
.dt-bar.split { border-top-width: 3px; }

.dt-bar.edge { cursor: default; }

.dt-bar:focus-visible {
  outline: 1px solid var(--wolf-focus);
  outline-offset: 1px;
}

.dt-bar-val {
  position: absolute;
  bottom: 100%;
  left: 0;
  right: 0;
  font-size: 9px;
  color: var(--wolf-faint);
  font-variant-numeric: tabular-nums;
  text-align: center;
  pointer-events: none;
}

/* Шкала недель под кривой. */
.dt-week-scale {
  display: flex;
  gap: 1px;
  height: 1rem;
}

.dt-week-label {
  flex: 1 1 0;
  min-width: 0;
  font-size: 8px;
  color: var(--wolf-faint);
  font-variant-numeric: tabular-nums;
  overflow: hidden;
  white-space: nowrap;
}

.dt-week-label.show { color: var(--wolf-muted); }

.dt-summary {
  margin: 0.3rem 0 0;
  font-size: 0.8rem;
  color: var(--wolf-ink);
  font-variant-numeric: tabular-nums;
}

.dt-hint {
  margin: 0.15rem 0 0;
  font-size: 0.72rem;
  color: var(--wolf-faint);
}

.dt-error {
  margin: 0;
  font-size: 0.78rem;
  color: var(--wolf-ink);
  border-bottom: 1px solid var(--wolf-ink);
}

.dt-mobile-form { display: none; }

.form-actions { margin-top: 0.25rem; }

/* Мобильный вид (≤700px): текстовая форма высот сегментов. */
@media (max-width: 700px) {
  .dt-track { cursor: default; }
  .dt-bar { cursor: default; }
  .dt-mobile-form {
    display: flex;
    gap: 1rem;
    flex-wrap: wrap;
  }
  .dt-seg-field {
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
    font-size: 0.75rem;
    color: var(--wolf-muted);
  }
  .dt-seg-field input {
    font: inherit;
    color: var(--wolf-ink);
    background: var(--wolf-surface);
    border: 1px solid var(--wolf-rule);
    padding: 0.3rem 0.5rem;
    width: 6rem;
  }
}
</style>
