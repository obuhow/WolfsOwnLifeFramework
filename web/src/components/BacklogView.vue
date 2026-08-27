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
  along with this program. If not, see <https://www.gnu.org/licenses/>.
-->
<script setup>
/**
 * Планирование → Бэклог (ticket 08).
 * Full-page period backlog: real scope (Неделя/Месяц), period navigation,
 * project grouping and an explicit move-to-week action. Viewing or switching
 * scope never creates TimeEntry rows.
 */
import { computed, onMounted, ref, watch } from 'vue'
import { apiBase, authHeaders } from '../api'
import BacklogGroups from './BacklogGroups.vue'
import { groupByProject, fetchProjectWeekHours, mondayOf, isoWeekId } from '../backlogGroups'

const scope = ref(localStorage.getItem('wolf_backlog_scope') || 'week')
const anchor = ref(new Date())

const items = ref([])
const delos = ref([])
const projects = ref([])
const projectHours = ref({})

const loading = ref(false)
const error = ref('')
const busy = ref(false)

function pad2(n) { return String(n).padStart(2, '0') }

/** Period identifier the API expects: ISO week id or YYYY-MM. */
const periodId = computed(() => {
  const d = anchor.value
  if (scope.value === 'month') return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}`
  return isoWeekId(d)
})

const periodLabel = computed(() => {
  const d = anchor.value
  if (scope.value === 'month') {
    return new Intl.DateTimeFormat('ru-RU', { month: 'long', year: 'numeric' }).format(d)
  }
  const monday = mondayOf(d)
  const sunday = new Date(monday)
  sunday.setDate(sunday.getDate() + 6)
  const fmt = x => `${pad2(new Date(x).getDate())}.${pad2(new Date(x).getMonth() + 1)}`
  return `${periodId.value} · ${fmt(monday)}–${fmt(sunday)}`
})

const groups = computed(() => groupByProject(
  items.value.map(item => ({
    ...item,
    title: item.deloTitle,
    projectIds: delos.value.find(d => d.id === item.deloId)?.projectIds || []
  })),
  projects.value,
  projectHours.value
))

function shiftPeriod(delta) {
  const d = new Date(anchor.value)
  if (scope.value === 'month') d.setMonth(d.getMonth() + delta)
  else d.setDate(d.getDate() + delta * 7)
  anchor.value = d
  load()
}

function goToday() {
  anchor.value = new Date()
  load()
}

function setScope(next) {
  if (scope.value === next) return
  scope.value = next
  localStorage.setItem('wolf_backlog_scope', next)
  load()
}

async function load() {
  const headers = authHeaders()
  if (!headers) return
  loading.value = true
  error.value = ''
  try {
    const [itemsRes, delosRes, projectsRes] = await Promise.all([
      fetch(`${apiBase()}/backlog?scope=${scope.value}&period=${encodeURIComponent(periodId.value)}`, { headers }),
      fetch(`${apiBase()}/delos`, { headers }),
      fetch(`${apiBase()}/projects`, { headers })
    ])
    if (!itemsRes.ok) throw new Error(`Бэклог: HTTP ${itemsRes.status}`)
    items.value = await itemsRes.json()
    delos.value = delosRes.ok ? await delosRes.json() : []
    projects.value = projectsRes.ok ? await projectsRes.json() : []
    projectHours.value = await fetchProjectWeekHours(apiBase(), headers, mondayOf(anchor.value))
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

/** Explicit user action — never automatic scheduling into slots. */
async function moveToWeek(item) {
  const headers = authHeaders(true)
  if (!headers) return
  busy.value = true
  error.value = ''
  try {
    const res = await fetch(`${apiBase()}/backlog/${item.id}/move-to-week`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ week: isoWeekId(anchor.value) })
    })
    if (!res.ok) {
      const body = await res.json().catch(() => ({}))
      throw new Error(body.message || `Перенос: HTTP ${res.status}`)
    }
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

async function removeItem(item) {
  const headers = authHeaders()
  if (!headers) return
  busy.value = true
  error.value = ''
  try {
    const res = await fetch(`${apiBase()}/backlog/${item.id}`, { method: 'DELETE', headers })
    if (!res.ok && res.status !== 204) throw new Error(`Удаление: HTTP ${res.status}`)
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

watch(scope, () => {})
onMounted(load)
</script>

<template>
  <div class="backlog-page">
    <header class="page-header backlog-header">
      <div>
        <h1>Бэклог</h1>
        <p class="eyebrow">Планирование · намерения периода по Проектам</p>
      </div>
      <div class="backlog-toolbar">
        <div class="scope-switch" role="group" aria-label="Охват бэклога">
          <button
            type="button"
            class="btn"
            :class="scope === 'week' ? 'btn-primary' : 'btn-ghost'"
            :aria-pressed="scope === 'week'"
            @click="setScope('week')"
          >Неделя</button>
          <button
            type="button"
            class="btn"
            :class="scope === 'month' ? 'btn-primary' : 'btn-ghost'"
            :aria-pressed="scope === 'month'"
            @click="setScope('month')"
          >Месяц</button>
        </div>
        <button type="button" class="btn btn-ghost" :disabled="loading" aria-label="Предыдущий период" @click="shiftPeriod(-1)">←</button>
        <button type="button" class="btn btn-ghost" :disabled="loading" @click="goToday">Текущий</button>
        <button type="button" class="btn btn-ghost" :disabled="loading" aria-label="Следующий период" @click="shiftPeriod(1)">→</button>
      </div>
    </header>

    <p class="period-label">{{ periodLabel }}</p>

    <BacklogGroups
      :groups="groups"
      :loading="loading"
      :error="error"
      :busy="busy"
      remove-label="Убрать из бэклога"
      :move-label="scope === 'month' ? 'Перенести в неделю' : ''"
      empty-text="В этом периоде бэклог пуст — добавьте Дела через «Дела»."
      @remove="removeItem"
      @move="moveToWeek"
    />
  </div>
</template>

<style scoped>
.backlog-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

.backlog-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
}

.scope-switch {
  display: inline-flex;
  gap: 0.4rem;
  margin-right: 0.5rem;
}

.period-label {
  margin: 0 0 14px;
  color: var(--wolf-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 640px) {
  .backlog-toolbar { width: 100%; }
}
</style>
