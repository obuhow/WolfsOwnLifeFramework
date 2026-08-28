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
 * Каркас «Диаграммы нагрузки» (release 0.8, тикет 02).
 *
 * Заменяет две плоские таблицы секции #load-plan на переключатель из четырёх вкладок
 * с единой загрузкой данных. Контракт оформления и общий хелпер записи живут здесь и
 * передаются будущим вкладкам (03–05) через provide/inject. Сами диаграммы в этом тикете
 * — заглушки («подключается в тикете NN»).
 */
import { computed, onMounted, onBeforeUnmount, provide, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiBase, authHeaders } from '../api'
import { useLoadChartWrite } from '../loadChartsWrite'

const route = useRoute()
const router = useRouter()

// --- Контракт вкладок --------------------------------------------------------
const TABS = [
  { key: 'curves', label: 'Кривые', ticket: '03' },
  { key: 'budget', label: 'Бюджет', ticket: '04' },
  { key: 'ladder', label: 'Лестница', ticket: '04' },
  { key: 'queue', label: 'Очередь', ticket: '05' },
]
const DEFAULT_TAB = 'curves'
const LS_KEY = 'wolf.loadCharts.tab'

// Приоритет при открытии: query (?chart=) → localStorage → «Кривые».
function resolveInitialTab() {
  const q = route.query.chart
  if (typeof q === 'string' && TABS.some((t) => t.key === q)) return q
  const ls = localStorage.getItem(LS_KEY)
  if (ls && TABS.some((t) => t.key === ls)) return ls
  return DEFAULT_TAB
}

const activeTab = ref(resolveInitialTab())
const tabRefs = ref([])

function setTabRef(el, i) {
  if (el) tabRefs.value[i] = el
}

function setTab(key) {
  activeTab.value = key
  localStorage.setItem(LS_KEY, key)
  // Вид передаётся ссылкой через query, без перезагрузки данных.
  if (route.query.chart !== key) {
    router.replace({ path: '/roadmap', query: { ...route.query, chart: key } })
  }
}

// Следим за внешним изменением ?chart (например, открытие /roadmap?chart=ladder).
watch(
  () => route.query.chart,
  (q) => {
    if (typeof q === 'string' && TABS.some((t) => t.key === q) && q !== activeTab.value) {
      activeTab.value = q
      localStorage.setItem(LS_KEY, q)
    }
  }
)

// Стрелочная навигация по переключателю вкладок (ARIA tablist pattern).
function onTabKeydown(e, i) {
  let next = null
  if (e.key === 'ArrowRight') next = (i + 1) % TABS.length
  else if (e.key === 'ArrowLeft') next = (i - 1 + TABS.length) % TABS.length
  else if (e.key === 'Home') next = 0
  else if (e.key === 'End') next = TABS.length - 1
  if (next === null) return
  e.preventDefault()
  setTab(TABS[next].key)
  tabRefs.value[next]?.focus()
}

// --- Единая загрузка данных --------------------------------------------------
const data = ref(null)
const loading = ref(true)
const error = ref('')
const empty = computed(() => !loading.value && !error.value && data.value && data.value.projects.length === 0)

async function load() {
  const headers = authHeaders()
  if (!headers) return
  loading.value = true
  error.value = ''
  try {
    const res = await fetch(`${apiBase()}/planning/load-charts`, { headers })
    if (res.status === 401 || res.status === 403) return // authHeaders уже увёл на логин
    if (!res.ok) throw new Error(`Диаграммы нагрузки: HTTP ${res.status}`)
    data.value = await res.json()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

// --- Общий хелпер записи (provide для вкладок 03–05) -------------------------
// Фабрика: вкладка вызывает makeWriter(url, body, opts), получает { error, commit, flush, cancel }.
function makeWriter(url, body, opts) {
  return useLoadChartWrite(url, body, opts)
}
provide('loadCharts', {
  data,
  makeWriter,
})

onMounted(load)
onBeforeUnmount(() => {
  // При размонтировании слить отложенные записи (на всякий случай).
  data.value = null
})
</script>

<template>
  <section id="load-plan" class="load-charts" aria-label="План нагрузки">
    <div class="section-heading">
      <div>
        <span class="eyebrow">Нагрузка</span>
        <h2>План нагрузки</h2>
      </div>
    </div>

    <p v-if="error" class="banner error">{{ error }}</p>

    <div v-else-if="loading" class="muted">Загрузка диаграмм…</div>

    <p v-else-if="empty" class="muted">Нет активных проектов</p>

    <div v-else class="load-charts-body">
      <!-- Переключатель вкладок (тихий контракт 0.3: без сигнальных цветов/alert). -->
      <div class="tabs" role="tablist" aria-label="Диаграммы нагрузки">
        <button
          v-for="(t, i) in TABS"
          :key="t.key"
          :ref="(el) => setTabRef(el, i)"
          type="button"
          class="tab"
          role="tab"
          :id="`load-chart-tab-${t.key}`"
          :aria-selected="activeTab === t.key"
          :aria-controls="`load-chart-panel-${t.key}`"
          :tabindex="activeTab === t.key ? 0 : -1"
          :class="{ active: activeTab === t.key }"
          @click="setTab(t.key)"
          @keydown="onTabKeydown($event, i)"
        >
          {{ t.label }}
        </button>
      </div>

      <!-- Панели вкладок. В этом тикете — заглушки; контент приходит тикетами 03–05. -->
      <div
        v-for="t in TABS"
        v-show="activeTab === t.key"
        :key="t.key"
        :id="`load-chart-panel-${t.key}`"
        class="tab-panel"
        role="tabpanel"
        :aria-labelledby="`load-chart-tab-${t.key}`"
      >
        <p class="chart-placeholder muted">подключается в тикете {{ t.ticket }}</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.load-charts { width: 100%; }

.section-heading { margin-bottom: 0.85rem; }
.section-heading h2 { margin: 0; font-size: 1rem; }
.section-heading .eyebrow {
  display: block;
  font-size: 0.7rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--wolf-muted);
}

/* Переключатель — тот же паттерн, что в SpheresView.vue. */
.tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--wolf-rule);
  margin-bottom: 16px;
}

.tab {
  padding: 8px 4px;
  margin-right: 20px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--wolf-muted);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}

.tab.active {
  color: var(--wolf-ink);
  border-bottom-color: var(--wolf-ink);
  font-weight: 600;
}

.tab-panel { min-height: 6rem; }

/* Заглушка вкладки — нейтральный текст, без иконок предупреждения/alert. */
.chart-placeholder {
  padding: 1rem 0;
  border-top: 1px solid var(--wolf-subrule);
}

.banner { padding: 0.6rem 0; margin: 0 0 0.75rem; border-radius: 0; background: transparent; font-size: 0.9rem; }
.banner.error { color: var(--wolf-ink); border-bottom: 1px solid var(--wolf-ink); }

.muted { color: var(--wolf-muted); }

/* Все часы и даты — моноширинные. */
.tab-panel :deep(*) { font-variant-numeric: tabular-nums; }
</style>
