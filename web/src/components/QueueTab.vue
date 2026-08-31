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
 * Вкладка «Очередь» (release 0.8, тикет 05, вариант 2c).
 *
 * Крайний полюс относительно «Бюджета»: вся недельная норма уходит на один проект
 * за раз, ноль переключений, но последний в очереди ждёт годы. Это инструмент
 * сравнения сценария, а не второй план — единственная вкладка релиза, которая
 * НИЧЕГО не пишет на сервер (п.6).
 *
 * Порядок очереди — представление, не хранимое поле (п.5). Базовый порядок =
 * projects[] из ответа API (уже отсортирован по title: ProjectRepository.
 * findByUserOrderByTitleAsc); пользовательская перестановка живёт в localStorage
 * (wolf.loadCharts.queueOrder) как список id. Проекты, появившиеся после
 * сохранения порядка, встают в конец; исчезнувшие id игнорируются.
 *
 * Прогноз каждого проекта в очереди считается той же формулой forecastByRate,
 * что на «Бюджете»/«Лестнице», но ставкой выступает вся недельная норма
 * (weeklyLimit), а не ставка проекта. Курсор идёт по очереди: старт следующего
 * проекта = финиш предыдущего. Полоса позиционируется от общей длительности
 * прохода всей очереди (left/width в %).
 */
import { computed, inject, ref, watch } from 'vue'
import { forecastByRate, formatFinish, formatHours, weeksBetween, toMonday } from '../loadChartsForecast'

const { data } = inject('loadCharts')

const projects = computed(() => data.value?.projects ?? [])
const weeklyLimit = computed(() => Number(data.value?.weeklyLimit ?? 0))
const startMonday = computed(() => data.value?.startMonday ?? null)

const QUEUE_LS = 'wolf.loadCharts.queueOrder'

function loadSavedIds() {
  try {
    const raw = localStorage.getItem(QUEUE_LS)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.filter((x) => typeof x === 'number') : []
  } catch {
    return []
  }
}

function persistIds(ids) {
  try {
    localStorage.setItem(QUEUE_LS, JSON.stringify(ids))
  } catch {
    /* localStorage недоступен — перестановка живёт только в текущей сессии */
  }
}

// Базовый порядок — title asc (как в бэкенд-репозитории findByUserOrderByTitleAsc).
function baseOrder() {
  return [...projects.value].sort((a, b) => a.title.localeCompare(b.title, 'ru'))
}

// Строит очередь проектов с учётом сохранённого порядка (п.5):
// сохранённые id, отфильтрованные от исчезнувших, + новые проекты в конец.
// Результат перезаписывает LS, закрепляя базу (новые уже в конце).
function buildQueue() {
  const all = baseOrder()
  const saved = loadSavedIds()
  if (!saved.length) {
    const ids = all.map((p) => p.id)
    persistIds(ids)
    return all
  }
  const byId = new Map(all.map((p) => [p.id, p]))
  const kept = saved.filter((id) => byId.has(id)).map((id) => byId.get(id))
  const seen = new Set(saved)
  const added = all.filter((p) => !seen.has(p.id))
  const result = [...kept, ...added]
  persistIds(result.map((p) => p.id))
  return result
}

// Очередь проектов (представление). Пересчитывается при загрузке данных
// и при действии «выше» (без перезагрузки данных).
const queue = ref([])

function rebuildFromData() {
  if (data.value && data.value.projects) queue.value = buildQueue()
}

// Данные могут уже быть загружены к моменту монтирования вкладки (v-show).
rebuildFromData()
watch(() => data.value, rebuildFromData)

// Действие «выше»: переставляет проект на позицию выше, пересчитывает сроки
// всех ниже без перезагрузки данных. НЕ пишет в БД (п.4, п.5).
function moveUp(index) {
  if (index <= 0) return
  const arr = [...queue.value]
  const [item] = arr.splice(index, 1)
  arr.splice(index - 1, 0, item)
  queue.value = arr
  persistIds(arr.map((p) => p.id))
}

// Расчёт очереди: курсор идёт по проектам, старт следующего = финиш предыдущего.
const schedule = computed(() => {
  const limit = weeklyLimit.value
  const sm = startMonday.value
  if (!queue.value.length || !sm) return { rows: [], totalWeeks: 0, finishIso: null }

  // Проход 1: недели и даты.
  const base = []
  let total = 0
  let cursor = toMonday(sm)
  for (const p of queue.value) {
    let weeks = 0
    let finishIso
    if (p.remainingHours > 0 && limit > 0) {
      const f = forecastByRate(p.remainingHours, limit, cursor)
      finishIso = f
      weeks = weeksBetween(cursor, f) ?? 0
    } else {
      // нулевой остаток либо нулевая норма — проект не сдвигает очередь.
      finishIso = fmtIso(cursor)
      weeks = 0
    }
    base.push({ p, startIso: fmtIso(cursor), finishIso, weeks })
    total += weeks
    cursor = toMonday(finishIso)
  }

  // Проход 2: геометрия полос от общей длительности прохода.
  let acc = 0
  const rows = base.map((b, i) => {
    const leftPct = total ? (acc / total) * 100 : 0
    const widthPct = total ? (b.weeks / total) * 100 : 0
    acc += b.weeks
    return {
      ...b.p,
      index: i,
      startIso: b.startIso,
      finishIso: b.finishIso,
      weeks: b.weeks,
      leftPct,
      widthPct,
    }
  })

  const finishIso = base.length ? base[base.length - 1].finishIso : null
  return { rows, totalWeeks: total, finishIso }
})

// Заливка полос — градация нейтральными токенами по позиции (п.7):
// первая --wolf-ink, вторая-третья --wolf-muted, дальше --wolf-faint.
function barClass(i) {
  if (i === 0) return 'ink'
  if (i <= 2) return 'muted'
  return 'faint'
}

function fmtIso(d) {
  const date = d instanceof Date ? new Date(d.getTime()) : new Date(d + 'T00:00:00Z')
  return date.toISOString().slice(0, 10)
}
</script>

<template>
  <div class="queue-tab">
    <!-- Подзаголовок (п.3): констатация сценария, без акцентов. -->
    <p class="queue-sub">
      Все {{ Math.round(weeklyLimit) }} ч в неделю уходят на один проект за раз.
      Полный проход: {{ schedule.totalWeeks }} недель до {{ formatFinish(schedule.finishIso) }}.
    </p>

    <!-- Единая CSS Grid система: заголовок + строки (п.8). -->
    <div class="queue-grid" role="table" aria-label="Очередь приоритетов проектов">
      <div class="queue-row queue-head" role="row">
        <span role="columnheader">#</span>
        <span role="columnheader">Проект</span>
        <span role="columnheader">Окно работ</span>
        <span role="columnheader">Старт → Финиш</span>
        <span role="columnheader" class="sr-col">Выше</span>
      </div>

      <div v-for="row in schedule.rows" :key="row.id" class="queue-row" role="row">
        <!-- Порядковый номер -->
        <span class="q-num" role="cell">{{ row.index + 1 }}</span>

        <!-- Название + подпись «N Дел · M ч» (п.1) -->
        <span class="q-project" role="cell">
          <span class="q-title">{{ row.title }}</span>
          <span class="q-sub">{{ row.deloCount }} Дел · {{ formatHours(row.effortHours) }}</span>
        </span>

        <!-- Полоса окна работ: позиция от общей длительности прохода (п.2, п.8) -->
        <span class="q-window" role="cell">
          <span
            class="q-bar"
            :class="barClass(row.index)"
            :style="{ left: row.leftPct + '%', width: Math.max(row.widthPct, 0) + '%' }"
            :title="`${formatFinish(row.startIso)} → ${formatFinish(row.finishIso)}`"
          ></span>
        </span>

        <!-- Старт → Финиш и число недель -->
        <span class="q-range" role="cell">
          <span class="q-range-text">{{ formatFinish(row.startIso) }} → {{ formatFinish(row.finishIso) }}</span>
          <span class="q-weeks">{{ row.weeks }} нед</span>
        </span>

        <!-- Действие «выше»: текстовая кнопка, у первой строки отсутствует (п.4, п.10) -->
        <span class="q-action" role="cell">
          <button
            v-if="row.index > 0"
            type="button"
            class="q-up"
            :aria-label="`Поднять выше: ${row.title}`"
            @click="moveUp(row.index)"
          >выше</button>
        </span>
      </div>
    </div>

    <!-- Подпись внизу проговаривает, что вкладка ничего не записывает (п.6). -->
    <p class="queue-note">
      Крайний вариант последовательности: ноль переключений, но последний проект в очереди
      ждёт годы. Сравните с вкладкой Бюджет, где всё идёт разом.
    </p>
  </div>
</template>

<style scoped>
.queue-tab { width: 100%; }

.queue-sub {
  margin: 0 0 0.85rem;
  font-size: 0.85rem;
  color: var(--wolf-muted);
  font-variant-numeric: tabular-nums;
}

/* Единая Grid координатная система для заголовка и всех строк (п.8). */
.queue-grid {
  display: grid;
  grid-template-columns: 2.5rem minmax(10rem, 1.4fr) minmax(8rem, 1.6fr) minmax(9rem, 1fr) 5rem;
  align-items: center;
  column-gap: 1rem;
  row-gap: 0.5rem;
}

.queue-head {
  font-size: 0.7rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--wolf-muted);
  border-bottom: 1px solid var(--wolf-rule);
  padding-bottom: 0.35rem;
}

.queue-row {
  display: grid;
  grid-template-columns: subgrid;
  grid-column: 1 / -1;
  align-items: center;
  padding: 0.4rem 0;
  border-bottom: 1px solid var(--wolf-subrule);
}

.q-num { font-size: 0.8rem; color: var(--wolf-muted); font-variant-numeric: tabular-nums; }

.q-title { display: block; font-size: 0.9rem; color: var(--wolf-ink); }
.q-sub { display: block; font-size: 0.72rem; color: var(--wolf-muted); }

/* Полоса окна работ: контейнер-дорожка + абсолютная полоса в % от общей длительности. */
.q-window {
  position: relative;
  height: 1rem;
  border: 1px solid var(--wolf-subrule);
  min-width: 0;
}
.q-bar {
  position: absolute;
  top: 0;
  bottom: 0;
  min-width: 2px; /* проект с нулевым остатком не выпадает из очереди (п.2) */
}
.q-bar.ink { background: var(--wolf-ink); }
.q-bar.muted { background: var(--wolf-muted); }
.q-bar.faint { background: var(--wolf-faint); }

.q-range { display: flex; flex-direction: column; font-size: 0.82rem; color: var(--wolf-ink); font-variant-numeric: tabular-nums; }
.q-weeks { font-size: 0.68rem; color: var(--wolf-faint); }

/* Действие «выше»: текстовая кнопка, --wolf-muted, при наведении --wolf-ink (п.4). */
.q-up {
  border: 0;
  background: transparent;
  padding: 0;
  font: inherit;
  font-size: 0.8rem;
  color: var(--wolf-muted);
  cursor: pointer;
}
.q-up:hover,
.q-up:focus-visible { color: var(--wolf-ink); }

.queue-note {
  margin: 0.85rem 0 0;
  font-size: 0.72rem;
  color: var(--wolf-muted);
}

/* Мобильный вид (≤700px): одноколоночные карточки строк (п.9). */
@media (max-width: 700px) {
  .queue-grid { display: block; }
  .queue-head { display: none; }
  .queue-row {
    display: grid;
    grid-template-columns: 1fr;
    gap: 0.3rem;
    padding: 0.6rem 0;
    border-bottom: 1px solid var(--wolf-rule);
  }
  .q-action { text-align: left; }
}
</style>
