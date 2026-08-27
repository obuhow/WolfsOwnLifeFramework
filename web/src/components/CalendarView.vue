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
 * Календарь: Неделя (ticket 04) и Месяц (ticket 08).
 * The month view is an overview, not a slot editor: it never creates or mutates
 * TimeEntry rows — opening a day navigates to the week register.
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiBase, authHeaders } from '../api'
import WeekView from './WeekView.vue'
import BacklogGroups from './BacklogGroups.vue'
import { groupByProject, fetchProjectWeekHours, mondayOf, isoWeekId } from '../backlogGroups'

const route = useRoute()
const router = useRouter()

const view = ref(route.query.view === 'month' ? 'month' : localStorage.getItem('wolf_calendar_view') || 'week')
const month = ref(route.query.date || new Date().toISOString().slice(0, 7))

const days = ref([])
const loading = ref(false)
const error = ref('')

const weekBacklog = ref([])
const monthBacklog = ref([])
const delos = ref([])
const projects = ref([])
const projectHours = ref({})
const backlogLoading = ref(false)

const weekdays = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс']
const todayIso = new Date().toISOString().slice(0, 10)

const monthTitle = computed(() =>
  new Intl.DateTimeFormat('ru-RU', { month: 'long', year: 'numeric' })
    .format(new Date(`${month.value}-01T00:00:00`))
)

const monthAnchor = computed(() => new Date(`${month.value}-01T00:00:00`))

function withProjects(list) {
  return list.map(item => ({
    ...item,
    title: item.deloTitle,
    projectIds: delos.value.find(d => d.id === item.deloId)?.projectIds || []
  }))
}

const weekGroups = computed(() => groupByProject(withProjects(weekBacklog.value), projects.value, projectHours.value))
const monthGroups = computed(() => groupByProject(withProjects(monthBacklog.value), projects.value, projectHours.value))

function syncUrl() {
  localStorage.setItem('wolf_calendar_view', view.value)
  router.replace({
    path: '/calendar',
    query: { view: view.value, date: view.value === 'month' ? month.value : undefined }
  })
}

function shiftMonth(delta) {
  const [y, m] = month.value.split('-').map(Number)
  const d = new Date(y, m - 1 + delta, 1)
  month.value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
  loadMonth()
}

function goThisMonth() {
  month.value = new Date().toISOString().slice(0, 7)
  loadMonth()
}

function setView(next) {
  view.value = next
  if (next === 'month') loadMonth()
  else syncUrl()
}

/** Read-only aggregation: month grid + real weekly/monthly backlog groups. */
async function loadMonth() {
  syncUrl()
  if (view.value !== 'month') return
  const headers = authHeaders()
  if (!headers) return
  loading.value = true
  backlogLoading.value = true
  error.value = ''
  try {
    const res = await fetch(`${apiBase()}/calendar/month?month=${month.value}`, { headers })
    if (!res.ok) throw new Error(`Календарь: HTTP ${res.status}`)
    const body = await res.json()
    days.value = body.days || []

    const weekId = isoWeekId(monthAnchor.value)
    const [weekRes, monthRes, delosRes, projectsRes] = await Promise.all([
      fetch(`${apiBase()}/backlog?scope=week&period=${encodeURIComponent(weekId)}`, { headers }),
      fetch(`${apiBase()}/backlog?scope=month&period=${month.value}`, { headers }),
      fetch(`${apiBase()}/delos`, { headers }),
      fetch(`${apiBase()}/projects`, { headers })
    ])
    weekBacklog.value = weekRes.ok ? await weekRes.json() : []
    monthBacklog.value = monthRes.ok ? await monthRes.json() : []
    delos.value = delosRes.ok ? await delosRes.json() : []
    projects.value = projectsRes.ok ? await projectsRes.json() : []
    projectHours.value = await fetchProjectWeekHours(apiBase(), headers, mondayOf(monthAnchor.value))
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
    backlogLoading.value = false
  }
}

/** Opening a day switches to the week register — the month never edits slots. */
function openDay(date) {
  router.push({ path: '/calendar', query: { view: 'week', date } })
  view.value = 'week'
}

onMounted(loadMonth)
</script>

<template>
  <div class="calendar-page">
    <header class="page-header calendar-header">
      <div>
        <h1>Календарь</h1>
        <p class="eyebrow">Неделя или месяц · записи времени</p>
      </div>
      <div class="calendar-controls">
        <button type="button" class="btn" :class="view === 'week' ? 'btn-primary' : 'btn-ghost'" :aria-pressed="view === 'week'" @click="setView('week')">Неделя</button>
        <button type="button" class="btn" :class="view === 'month' ? 'btn-primary' : 'btn-ghost'" :aria-pressed="view === 'month'" @click="setView('month')">Месяц</button>
      </div>
    </header>

    <WeekView v-if="view === 'week'" />

    <template v-else>
      <div class="month-toolbar">
        <div class="month-nav">
          <button type="button" class="btn btn-ghost" :disabled="loading" aria-label="Предыдущий месяц" @click="shiftMonth(-1)">←</button>
          <strong class="month-title">{{ monthTitle }}</strong>
          <button type="button" class="btn btn-ghost" :disabled="loading" aria-label="Следующий месяц" @click="shiftMonth(1)">→</button>
          <button type="button" class="btn btn-ghost" :disabled="loading" @click="goThisMonth">Текущий месяц</button>
        </div>
        <p class="month-hint">Обзор месяца. Постановка времени — в «Неделе» и «Сегодня».</p>
      </div>

      <p v-if="error" class="alert alert-error">{{ error }}</p>
      <div v-if="loading" class="loading">Загрузка…</div>

      <div v-else class="calendar-layout">
        <div class="month-scroll" role="region" aria-label="Сетка месяца, прокрутка по горизонтали" tabindex="0">
          <div class="month-grid">
            <div v-for="weekday in weekdays" :key="weekday" class="weekday">{{ weekday }}</div>
            <button
              v-for="day in days"
              :key="day.date"
              type="button"
              class="month-day"
              :class="{ muted: day.outOfMonth, today: day.date === todayIso }"
              :title="`${day.date} — открыть неделю`"
              @click="openDay(day.date)"
            >
              <strong class="day-num">{{ Number(day.date.slice(8)) }}</strong>
              <span v-for="entry in day.entries.slice(0, 3)" :key="entry.id" class="month-entry">
                <span class="entry-time">{{ entry.startAt.slice(11, 16) }}</span>
                {{ entry.deloTitle }}
              </span>
              <span v-if="day.totalCount > 3" class="more">ещё {{ day.totalCount - 3 }}</span>
            </button>
          </div>
        </div>

        <aside class="month-asides" aria-label="Бэклоги периода">
          <section class="month-aside-section" aria-label="Бэклог недели">
            <h2>Бэклог недели</h2>
            <BacklogGroups
              :groups="weekGroups"
              :loading="backlogLoading"
              empty-text="Бэклог недели пуст."
            />
          </section>
          <section class="month-aside-section" aria-label="Бэклог месяца">
            <h2>Бэклог месяца</h2>
            <BacklogGroups
              :groups="monthGroups"
              :loading="backlogLoading"
              empty-text="Бэклог месяца пуст."
            />
          </section>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.calendar-header,
.calendar-controls,
.month-nav {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.calendar-header { justify-content: space-between; flex-wrap: wrap; }

.month-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.75rem;
  margin: 0 0 12px;
}

.month-nav { gap: 0.4rem; flex-wrap: wrap; }
.month-title { font-size: 0.95rem; }

.month-hint {
  margin: 0;
  color: var(--wolf-muted);
  font-size: 12px;
}

.calendar-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 268px;
  gap: 0 24px;
  align-items: start;
}

.month-scroll { overflow-x: auto; min-width: 0; }

.month-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(6rem, 1fr));
  border-top: 1px solid var(--wolf-rule);
  border-left: 1px solid var(--wolf-rule);
  min-width: 44rem;
}

.weekday {
  padding: 0.5rem 0.4rem;
  text-align: center;
  background: var(--wolf-surface);
  color: var(--wolf-muted);
  font-size: 0.72rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  border-right: 1px solid var(--wolf-rule);
  border-bottom: 1px solid var(--wolf-rule);
}

.month-day {
  min-height: 7.5rem;
  padding: 0.45rem 0.5rem;
  text-align: left;
  border: 0;
  border-right: 1px solid var(--wolf-rule);
  border-bottom: 1px solid var(--wolf-rule);
  background: var(--wolf-surface);
  display: grid;
  align-content: start;
  gap: 0.2rem;
  cursor: pointer;
  font: inherit;
  color: var(--wolf-ink);
}

.month-day:hover { background: var(--wolf-hover); }
.month-day.muted { color: var(--wolf-muted); }
.month-day.muted .day-num { color: var(--wolf-muted); }
.month-day.today { box-shadow: inset 0 -2px 0 var(--wolf-ink); }

.day-num { font-size: 0.82rem; font-variant-numeric: tabular-nums; font-weight: 600; }

.month-entry {
  font-size: 0.7rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--wolf-ink);
}

.entry-time { color: var(--wolf-muted); font-variant-numeric: tabular-nums; }

.more { font-size: 0.7rem; color: var(--wolf-muted); }

.month-asides {
  display: grid;
  gap: 22px;
  min-width: 0;
  border-left: 1px solid var(--wolf-rule);
  padding-left: 24px;
}

.month-aside-section h2 {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 600;
}

@media (max-width: 900px) {
  .calendar-layout { grid-template-columns: 1fr; gap: 24px 0; }
  .month-asides { border-left: 0; padding-left: 0; padding-top: 8px; border-top: 1px solid var(--wolf-rule); }
}
</style>
