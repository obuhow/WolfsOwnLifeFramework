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
 * Вкладка «Бюджет» (release 0.8, тикет 04, вариант 2a).
 *
 * Строка на проект: название и подпись «Область · N Дел»; ползунок «часов в неделю»;
 * объём работ; полоса окна работ; дата финиша. Одна CSS Grid система для заголовка и
 * всех строк. Запись ставки → PUT /api/v1/gantt/week-plans на текущую ISO-неделю
 * (общий хелпер тикета 02: дебаунс 400 мс, откат при ошибке). Исторические недели
 * не переписываются.
 *
 * Ставка (hours/week) живёт в общем стейте rates из LoadCharts.vue, поэтому изменение
 * на этой вкладке сразу отражается на «Лестнице» без перезагрузки (п.12).
 */
import { computed, inject, ref, watch } from 'vue'
import { apiBase, authHeaders } from '../api'
import { forecastByRate, formatFinish, formatHours, isoYearWeek } from '../loadChartsForecast'
import DistributeTimeModal from './DistributeTimeModal.vue'

const { data, rates, makeWriter } = inject('loadCharts')

const projects = computed(() => data.value?.projects ?? [])
const startMonday = computed(() => data.value?.startMonday ?? null)

// Максимальный прогноз среди проектов — для масштаба полос окон работ (п.4).
const maxForecastDays = computed(() => {
  let max = 0
  for (const p of projects.value) {
    const iso = forecastByRate(p.remainingHours, projectRate(p), startMonday.value)
    if (iso) {
      const days = (new Date(iso + 'T00:00:00Z') - new Date(startMonday.value + 'T00:00:00Z')) / 86400000
      if (days > max) max = days
    }
  }
  return max
})

// Строка бюджета в шапке (п.6): распределено / свободно / перебор — текстом, без цвета.
const weeklyLimit = computed(() => Number(data.value?.weeklyLimit ?? 0))
const allocated = computed(() =>
  projects.value.reduce((sum, p) => sum + (Number(rates[p.id] ?? p.weeklyPlanHours) || 0), 0)
)
const freeOrOver = computed(() => {
  const diff = Math.round(weeklyLimit.value - allocated.value)
  return { over: diff < 0, amount: Math.abs(diff) }
})

function projectRate(p) {
  const v = rates[p.id]
  return v != null ? Number(v) : Number(p.weeklyPlanHours || 0)
}

function forecastIso(p) {
  return forecastByRate(p.remainingHours, projectRate(p), startMonday.value)
}

function forecastDays(p) {
  const iso = forecastIso(p)
  if (!iso) return 0
  return Math.max(0, (new Date(iso + 'T00:00:00Z') - new Date(startMonday.value + 'T00:00:00Z')) / 86400000)
}

function actualForecast(p) {
  const map = data.value?.actualForecast
  if (!map || typeof map.get !== 'function') return null
  return map.get(p.id) || null
}

// --- Запись ставки -------------------------------------------------------------
const writers = {}
function ensureWriter(p) {
  if (writers[p.id]) return writers[p.id]
  const { isoYear, isoWeek } = isoYearWeek(startMonday.value)
  const url = `${apiBase()}/gantt/week-plans`
  const w = makeWriter(
    url,
    { projectId: p.id, isoYear, isoWeek, planHours: 0 },
    {
      localLabel: `часы «${p.title}»`,
      onError: () => {
        // откат к серверному значению
        rates[p.id] = p.weeklyPlanHours
      },
    }
  )
  writers[p.id] = w
  return w
}

const sliderErrors = ref({})

function onSliderInput(p, e) {
  const value = Number(e.target.value)
  rates[p.id] = value
  const w = ensureWriter(p)
  w.error.value = ''
  const { isoYear, isoWeek } = isoYearWeek(startMonday.value)
  // дебаунс 400 мс через общий хелпер; тело PUT обновляем текущей ставкой
  w.commit({ projectId: p.id, isoYear, isoWeek, planHours: value })
}

// Отслеживаем ошибки записи каждого проекта для показа строки под диаграммой.
watch(
  () => projects.value.map((p) => ensureWriter(p).error.value),
  (errs) => {
    const next = {}
    projects.value.forEach((p, i) => {
      if (errs[i]) next[p.id] = errs[i]
    })
    sliderErrors.value = next
  },
  { deep: true }
)

// --- Окно «Распределить время» (release 1.1, тикет 04) ----------------------
// Кнопка у плановых часов проекта открывает ручной редактор кривой: суммарные
// плановые часы (totalPlanHours) раскладываются по ISO-неделям диапазона дат,
// кривая делится на 2 сегмента и перетаскивается с инвариантом Σ = const.
// Детали проекта (startDate/endDate/totalPlanHours) подтягиваются с бэкенда —
// в load-charts их нет, а в GET /projects/{id} они есть.
const dtProject = ref(null) // проект из load-charts (id, title)
const dtDetail = ref(null) // ProjectDetailResponse
const dtLoading = ref(false)

async function openDistribute(p) {
  dtLoading.value = true
  dtDetail.value = null
  try {
    const headers = authHeaders()
    if (!headers) return
    const res = await fetch(`${apiBase()}/projects/${p.id}`, { headers })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    dtDetail.value = await res.json()
    dtProject.value = p
  } catch (e) {
    // Тихий контракт: ошибка открытия — нейтральной строкой под диаграммой.
    sliderErrors.value = { ...sliderErrors.value, [p.id]: `Не удалось открыть: ${e.message}` }
  } finally {
    dtLoading.value = false
  }
}
function closeDistribute() {
  dtProject.value = null
  dtDetail.value = null
}
</script>

<template>
  <div class="budget-tab">
    <!-- Строка бюджета в шапке вкладки (п.6): текстом, без сигнального цвета. -->
    <p class="budget-summary">
      <template v-if="!freeOrOver.over">
        распределено {{ Math.round(allocated) }} ч · свободно {{ freeOrOver.amount }} ч
      </template>
      <template v-else>
        распределено {{ Math.round(allocated) }} ч — перебор на {{ freeOrOver.amount }} ч
      </template>
    </p>

    <!-- Единая CSS Grid система: заголовок + строки. -->
    <div class="budget-grid" role="table" aria-label="Бюджет часов по проектам">
      <div class="budget-row budget-head" role="row">
        <span role="columnheader">Проект</span>
        <span role="columnheader">Часов в неделю</span>
        <span role="columnheader">Объём</span>
        <span role="columnheader">Окно работ</span>
        <span role="columnheader">Финиш</span>
      </div>

      <div v-for="p in projects" :key="p.id" class="budget-row" role="row">
        <!-- Название + подпись «Область · N Дел» -->
        <span class="b-project" role="cell">
          <span class="b-title">{{ p.title }}</span>
          <span class="b-sub">{{ p.lifeAreaName }} · {{ p.deloCount }} Дел</span>
        </span>

        <!-- Ползунок 0..20 step 1 -->
        <span class="b-rate" role="cell">
          <input
            type="range"
            min="0"
            max="20"
            step="1"
            :value="projectRate(p)"
            :aria-label="`Часов в неделю: ${p.title}`"
            @input="onSliderInput(p, $event)"
          />
          <span class="b-rate-label">
            <template v-if="projectRate(p) > 0">{{ formatHours(projectRate(p)) }}</template>
            <template v-else>заморожен</template>
          </span>
          <!-- Тикет 04: ручной редактор кривой «Распределить время». -->
          <button
            type="button"
            class="b-distribute"
            :disabled="dtLoading"
            :aria-label="`Распределить время: ${p.title}`"
            @click="openDistribute(p)"
          >Распределить время</button>
          <span v-if="sliderErrors[p.id]" class="b-rate-error">{{ sliderErrors[p.id] }}</span>
        </span>

        <!-- Объём работ -->
        <span class="b-effort" role="cell">
          {{ formatHours(p.effortHours) }}
          <span v-if="p.effortSource === 'ESTIMATED'" class="b-estimated">оценка</span>
        </span>

        <!-- Полоса окна работ -->
        <span class="b-window" role="cell">
          <span
            class="b-window-bar"
            :class="{ empty: projectRate(p) === 0 }"
            :style="{ width: maxForecastDays ? Math.max(2, (forecastDays(p) / maxForecastDays) * 100) + '%' : '0%' }"
          ></span>
        </span>

        <!-- Финиш + фактический прогноз под ним -->
        <span class="b-finish" role="cell">
          <span :class="{ frozen: projectRate(p) === 0 }">{{ formatFinish(forecastIso(p)) }}</span>
          <span v-if="actualForecast(p)" class="b-actual">по факту: {{ formatFinish(actualForecast(p)) }}</span>
        </span>
      </div>
    </div>

    <!-- Окно «Распределить время» (release 1.1, тикет 04). -->
    <DistributeTimeModal
      v-if="dtProject && dtDetail"
      :project="dtProject"
      :detail="dtDetail"
      @close="closeDistribute"
    />
  </div>
</template>

<style scoped>
.budget-tab { width: 100%; }

.budget-summary {
  margin: 0 0 0.75rem;
  font-size: 0.85rem;
  color: var(--wolf-ink);
}

.budget-grid {
  display: grid;
  grid-template-columns: minmax(10rem, 1.4fr) 11rem minmax(5rem, 0.7fr) minmax(6rem, 1fr) minmax(7rem, 0.9fr);
  align-items: center;
  column-gap: 1rem;
  row-gap: 0.5rem;
}

.budget-head {
  font-size: 0.7rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--wolf-muted);
  border-bottom: 1px solid var(--wolf-rule);
  padding-bottom: 0.35rem;
}

.budget-row {
  display: grid;
  grid-template-columns: subgrid;
  grid-column: 1 / -1;
  align-items: center;
  padding: 0.4rem 0;
  border-bottom: 1px solid var(--wolf-subrule);
}

.b-title { display: block; font-size: 0.9rem; color: var(--wolf-ink); }
.b-sub { display: block; font-size: 0.72rem; color: var(--wolf-muted); }

.b-rate { display: flex; flex-direction: column; gap: 0.15rem; }
.b-rate input[type='range'] { width: 100%; accent-color: var(--wolf-ink); }
.b-rate-label { font-size: 0.78rem; color: var(--wolf-muted); font-variant-numeric: tabular-nums; }
.b-rate-error { font-size: 0.72rem; color: var(--wolf-ink); border-bottom: 1px solid var(--wolf-ink); }

/* Кнопка ручного редактора (тикет 04) — текстовая, тихий контракт. */
.b-distribute {
  align-self: flex-start;
  border: 0;
  border-bottom: 1px solid var(--wolf-ink);
  background: transparent;
  color: var(--wolf-ink);
  font: inherit;
  font-size: 0.72rem;
  padding: 0;
  cursor: pointer;
  width: fit-content;
}
.b-distribute:hover { color: var(--wolf-muted); border-bottom-color: var(--wolf-muted); }
.b-distribute:disabled { color: var(--wolf-faint); border-bottom-color: transparent; cursor: default; }

.b-effort { font-size: 0.85rem; color: var(--wolf-ink); font-variant-numeric: tabular-nums; }
.b-estimated { font-size: 0.68rem; color: var(--wolf-faint); margin-left: 0.3rem; }

.b-window { display: flex; align-items: center; min-height: 1rem; }
.b-window-bar {
  display: block;
  height: 0.6rem;
  background: var(--wolf-subrule);
  border-left: 2px solid var(--wolf-ink);
  min-width: 2px;
}
.b-window-bar.empty { border-left: 0; background: transparent; }

.b-finish { display: flex; flex-direction: column; font-size: 0.85rem; color: var(--wolf-ink); font-variant-numeric: tabular-nums; }
.b-finish .frozen { color: var(--wolf-faint); }
.b-actual { font-size: 0.7rem; color: var(--wolf-muted); }

/* Мобильный вид (≤700px): одноколоночные карточки строк (п.13). */
@media (max-width: 700px) {
  .budget-grid { display: block; }
  .budget-head { display: none; }
  .budget-row {
    display: grid;
    grid-template-columns: 1fr;
    gap: 0.3rem;
    padding: 0.6rem 0;
    border-bottom: 1px solid var(--wolf-rule);
  }
}
</style>
