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
 * Вкладка «Кривые» (release 0.8, тикет 03, вариант 3a).
 *
 * Дорожка на проект со ступенчатой кривой часов в неделю на горизонте 18 месяцев.
 * Геометрия и взаимодействие каждой дорожки вынесены в переиспользуемый
 * LoadCurveLane (тикет 06) — здесь только шкала месяцев, набор дорожек и
 * суммарная загрузка по месяцам против недельной нормы.
 *
 * Данные приходят из inject('loadCharts').data (GET /planning/load-charts).
 * Запись каждой дорожки — через LoadCurveLane (useLoadChartWrite, PUT/DELETE
 * /projects/{id}/load-curve, дебаунс 400 мс, откат при ошибке).
 *
 * Фильтр (тикет 07): пропсы selectedProjectIds и monthlyLoad позволяют
 * LoadCharts управлять видимостью дорожек и суммарной строкой — снятые с
 * выбора проекты скрываются, а суммарная загрузка пересчитывается на клиенте.
 */
import { computed, inject } from 'vue'
import LoadCurveLane from './LoadCurveLane.vue'

const { data } = inject('loadCharts')

const props = defineProps({
  selectedProjectIds: { type: Object, default: null }, // Set<Long> либо null = все
  monthlyLoad: { type: Array, default: null }, // переопределённая суммарная строка
})

const HOURS_MAX = 20
const horizonMonths = computed(() => data.value?.horizonMonths ?? 18)
const startMonday = computed(() => data.value?.startMonday ?? null)
const projects = computed(() => data.value?.projects ?? [])
const weeklyLimit = computed(() => data.value?.weeklyLimit ?? 0)
const monthlyLoad = computed(() => props.monthlyLoad ?? data.value?.monthlyLoad ?? [])
// видимые дорожки: либо все, либо только выбранные в фильтре
const visibleProjects = computed(() =>
  props.selectedProjectIds ? projects.value.filter((p) => props.selectedProjectIds.has(p.id)) : projects.value
)

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

const limitTopPct = computed(() => {
  const h = weeklyLimit.value
  return Math.max(0, Math.min(100, (h / HOURS_MAX) * 100))
})

const overloadText = computed(() => {
  let peak = 0
  for (const m of monthlyLoad.value) peak = Math.max(peak, m.hours ?? 0)
  const limit = weeklyLimit.value
  if (peak > limit) {
    return `Пик загрузки ${fmtHours(peak)} — перебор на ${fmtHours(Math.round((peak - limit) * 100) / 100)}`
  }
  return `Пик загрузки ${fmtHours(peak)} из ${fmtHours(limit)}. В лимите.`
})

function monthLeftPct(idx) {
  return (idx / horizonMonths.value) * 100
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

    <!-- Дорожки по проектам (каждая — LoadCurveLane). -->
    <div class="load-lanes">
      <LoadCurveLane
        v-for="p in visibleProjects"
        :key="p.id"
        :project-id="p.id"
        :title="p.title"
        :start-monday="startMonday"
        :horizon-months="horizonMonths"
        :curve="p.curve"
      />
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
.load-lanes,
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
</style>
