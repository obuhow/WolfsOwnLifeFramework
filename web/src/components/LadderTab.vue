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
 * Вкладка «Лестница» (release 0.8, тикет 04, вариант 2b).
 *
 * Сверху — стопка распределения нормы недели по проектам с заштрихованным свободным
 * остатком. Снизу — на каждый проект четыре колонки «Меньше / Сейчас / Больше / Максимум»,
 * рассчитанные от текущей ставки как [max(1, h−3), h, h+3, h+6] (без дублей и нулей).
 * В каждой ячейке: ставка, дата финиша (той же формулой forecastByRate, что на бэкенде),
 * число недель. Прогнозы Лестницы и Бюджета на одинаковой ставке совпадают (п.11).
 *
 * Читает общий стейт ставки rates из LoadCharts.vue — текущая колонка это rates[projectId],
 * то есть ставка из вкладки «Бюджет» (п.12). Изменение ставки на «Бюджете» пересчитывает
 * «Лестницу» без перезагрузки.
 */
import { computed, inject } from 'vue'
import { forecastByRate, formatFinish, formatHours, ladderRates, weeksBetween } from '../loadChartsForecast'

const { data, rates } = inject('loadCharts')

const projects = computed(() => data.value?.projects ?? [])
const weeklyLimit = computed(() => Number(data.value?.weeklyLimit ?? 0))
const startMonday = computed(() => data.value?.startMonday ?? null)

// Стопка распределения: доля каждого проекта от недельной нормы.
// Свободный остаток — заштрихованная зона.
const allocated = computed(() =>
  projects.value.reduce((sum, p) => sum + (Number(rates[p.id] ?? p.weeklyPlanHours) || 0), 0)
)
const freeReserve = computed(() => Math.max(0, Math.round(weeklyLimit.value - allocated.value)))
const overReserve = computed(() => Math.max(0, Math.round(allocated.value - weeklyLimit.value)))

function projectRate(p) {
  const v = rates[p.id]
  return v != null ? Number(v) : Number(p.weeklyPlanHours || 0)
}

// Сегменты стопки: { id, hours, widthPct }
const stack = computed(() => {
  const total = weeklyLimit.value > 0 ? weeklyLimit.value : Math.max(1, allocated.value)
  return projects.value.map((p) => {
    const h = projectRate(p)
    return { id: p.id, title: p.title, hours: h, widthPct: total > 0 ? (h / total) * 100 : 0 }
  })
})

// Лестница сроков: четыре ставки от текущей (h). Число колонок может быть < 4
// при отбрасывании дублей/нулей (п.10) — grid подстраивается под maxCols.
function ladderFor(p) {
  const h = projectRate(p)
  return ladderRates(h).map((rate) => {
    const iso = forecastByRate(p.remainingHours, rate, startMonday.value)
    return {
      rate,
      isCurrent: rate === h,
      finish: formatFinish(iso),
      weeks: weeksBetween(startMonday.value, iso),
    }
  })
}

const maxCols = computed(() =>
  Math.max(4, ...projects.value.map((p) => ladderFor(p).length))
)
const HEAD_LABELS = ['Меньше', 'Сейчас', 'Больше', 'Максимум']
const headLabels = computed(() => HEAD_LABELS.slice(0, maxCols.value))
const rowGridStyle = computed(() => ({
  gridTemplateColumns: `minmax(9rem, 1.4fr) repeat(${maxCols.value}, minmax(5rem, 1fr))`,
}))
</script>

<template>
  <div class="ladder-tab">
    <!-- Стопка распределения нормы недели по проектам (п.8). -->
    <div class="stack" role="img" :aria-label="`Распределение недели: распределено ${formatHours(allocated)}, свободно ${freeReserve} ч`">
      <div
        v-for="s in stack"
        :key="s.id"
        class="stack-seg"
        :style="{ width: s.widthPct + '%' }"
        :title="`${s.title}: ${formatHours(s.hours)}`"
      >
        <span v-if="s.widthPct > 8" class="stack-seg-label">{{ formatHours(s.hours) }}</span>
      </div>
      <div v-if="freeReserve > 0" class="stack-free" :style="{ width: (weeklyLimit > 0 ? (freeReserve / weeklyLimit) * 100 : 0) + '%' }"></div>
    </div>
    <!-- Подзаголовок: констатация, не предупреждение (п.9). -->
    <p v-if="overReserve === 0" class="ladder-sub">Свободный резерв недели: {{ freeReserve }} ч</p>
    <p v-else class="ladder-sub">Сумма превышает норму. Уберите часы у одного проекта, чтобы освободить другой.</p>

    <!-- Лестница сроков (п.10). -->
    <div class="ladder-grid" role="table" aria-label="Лестница сроков по ставкам часов">
      <div class="ladder-row ladder-head" role="row" :style="rowGridStyle">
        <span role="columnheader">Проект</span>
        <span v-for="(label, i) in headLabels" :key="i" role="columnheader">{{ label }}</span>
      </div>

      <div v-for="p in projects" :key="p.id" class="ladder-row" role="row" :style="rowGridStyle">
        <span class="l-project" role="cell">
          <span class="l-title">{{ p.title }}</span>
          <span class="l-sub">{{ p.lifeAreaName }} · {{ p.deloCount }} Дел</span>
        </span>
        <span
          v-for="cell in ladderFor(p)"
          :key="cell.rate"
          class="l-cell"
          :class="{ current: cell.isCurrent }"
          role="cell"
        >
          <span class="l-rate">{{ cell.rate }} ч/нед</span>
          <span class="l-finish">{{ cell.finish }}</span>
          <span v-if="cell.weeks != null" class="l-weeks">{{ cell.weeks }} нед</span>
        </span>
      </div>
    </div>

    <!-- Связь вкладок (п.12). -->
    <p class="ladder-link">Тёмная колонка — текущая ставка часов из вкладки Бюджет</p>
  </div>
</template>

<style scoped>
.ladder-tab { width: 100%; }

/* Стопка распределения (п.8): рамка 1px --wolf-ink, свободный остаток — штриховка. */
.stack {
  display: flex;
  height: 1.6rem;
  border: 1px solid var(--wolf-ink);
  overflow: hidden;
  margin-bottom: 0.4rem;
}
.stack-seg {
  background: var(--wolf-subrule);
  border-right: 1px solid var(--wolf-rule);
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  overflow: hidden;
}
.stack-seg-label {
  font-size: 0.68rem;
  color: var(--wolf-muted);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.stack-free {
  flex: 0 0 auto;
  background: repeating-linear-gradient(
    45deg,
    var(--wolf-subrule),
    var(--wolf-subrule) 4px,
    var(--wolf-rule) 4px,
    var(--wolf-rule) 8px
  );
}

.ladder-sub {
  margin: 0 0 0.85rem;
  font-size: 0.8rem;
  color: var(--wolf-muted);
}

/* Лестница сроков — та же CSS Grid система; число колонок динамическое (п.10). */
.ladder-grid { width: 100%; }
.ladder-head {
  font-size: 0.7rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--wolf-muted);
  border-bottom: 1px solid var(--wolf-rule);
  padding-bottom: 0.35rem;
}
.ladder-row {
  display: grid;
  align-items: stretch;
  column-gap: 0.6rem;
  border-bottom: 1px solid var(--wolf-subrule);
}
.l-title { display: block; font-size: 0.88rem; color: var(--wolf-ink); }
.l-sub { display: block; font-size: 0.7rem; color: var(--wolf-muted); }

.l-cell {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  padding: 0.45rem 0.2rem;
  border-left: 1px solid var(--wolf-rule);
  font-variant-numeric: tabular-nums;
  color: var(--wolf-muted);
}
/* Текущая ставка — весом шрифта и цветом чернил, без акцента (п.10). */
.l-cell.current {
  color: var(--wolf-ink);
  font-weight: 600;
  border-left: 2px solid var(--wolf-ink);
}
.l-rate { font-size: 0.82rem; }
.l-finish { font-size: 0.82rem; }
.l-weeks { font-size: 0.68rem; color: var(--wolf-faint); }

.ladder-link {
  margin: 0.85rem 0 0;
  font-size: 0.72rem;
  color: var(--wolf-muted);
}

/* Мобильный вид (≤700px): четыре колонки → четыре строки внутри карточки (п.13). */
@media (max-width: 700px) {
  .ladder-head { display: none; }
  .ladder-row {
    display: grid;
    grid-template-columns: 1fr;
    gap: 0.2rem;
    padding: 0.6rem 0;
    border-bottom: 1px solid var(--wolf-rule);
  }
  .l-cell {
    flex-direction: row;
    gap: 0.6rem;
    align-items: baseline;
    border-left: 0;
    padding: 0.2rem 0;
  }
  .l-cell.current { border-left: 2px solid var(--wolf-ink); padding-left: 0.4rem; }
}
</style>
