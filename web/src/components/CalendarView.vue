<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiBase, authHeaders } from '../api'
import WeekView from './WeekView.vue'

const route = useRoute(); const router = useRouter()
const view = ref(route.query.view === 'month' ? 'month' : localStorage.getItem('wolf_calendar_view') || 'week')
const month = ref(route.query.date || new Date().toISOString().slice(0, 7))
const days = ref([]); const loading = ref(false); const error = ref(''); const backlog = ref([]); const backlogOpen = ref(true)
const weekdays = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс']
const monthTitle = computed(() => new Intl.DateTimeFormat('ru-RU', { month: 'long', year: 'numeric' }).format(new Date(`${month.value}-01T00:00:00`)))
function syncUrl() { localStorage.setItem('wolf_calendar_view', view.value); router.replace({ path: '/calendar', query: { view: view.value, date: view.value === 'month' ? month.value : undefined } }) }
function shiftMonth(delta) { const [y, m] = month.value.split('-').map(Number); const d = new Date(y, m - 1 + delta, 1); month.value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`; loadMonth() }
function today() { month.value = new Date().toISOString().slice(0, 7); loadMonth() }
async function loadMonth() { syncUrl(); if (view.value !== 'month') return; loading.value = true; try { const headers = authHeaders(); const res = await fetch(`${apiBase()}/calendar/month?month=${month.value}`, { headers }); if (!res.ok) throw new Error(`Календарь: HTTP ${res.status}`); const body = await res.json(); days.value = body.days || []; const backlogRes = await fetch(`${apiBase()}/backlog?scope=month&period=${month.value}`, { headers }); backlog.value = backlogRes.ok ? await backlogRes.json() : [] } catch (e) { error.value = e.message } finally { loading.value = false } }
function openDay(date) { router.push({ path: '/week', query: { date } }) }
onMounted(loadMonth)
</script>
<template>
  <div class="calendar-page">
    <header class="page-header calendar-header"><div><h1>Календарь</h1><p class="eyebrow">Неделя или месяц · записи времени</p></div><div class="calendar-controls"><button class="btn" :class="view === 'week' ? 'btn-primary' : 'btn-ghost'" @click="view = 'week'; syncUrl()">Неделя</button><button class="btn" :class="view === 'month' ? 'btn-primary' : 'btn-ghost'" @click="view = 'month'; loadMonth()">Месяц</button></div></header>
    <WeekView v-if="view === 'week'" />
    <template v-else><div class="month-toolbar"><button class="btn btn-ghost" @click="shiftMonth(-1)">←</button><strong>{{ monthTitle }}</strong><button class="btn btn-ghost" @click="shiftMonth(1)">→</button><button class="btn btn-ghost" @click="today">Сегодня</button></div><p v-if="error" class="alert alert-error">{{ error }}</p><div v-if="loading" class="loading">Загрузка…</div><div v-else class="calendar-layout"><div class="month-grid"><div v-for="weekday in weekdays" :key="weekday" class="weekday">{{ weekday }}</div><button v-for="day in days" :key="day.date" class="month-day" :class="{ muted: day.outOfMonth, today: day.date === new Date().toISOString().slice(0, 10) }" @click="openDay(day.date)"><strong>{{ Number(day.date.slice(8)) }}</strong><span v-for="entry in day.entries.slice(0, 3)" :key="entry.id" class="month-entry">{{ entry.startAt.slice(11, 16) }} {{ entry.deloTitle }}</span><span v-if="day.totalCount > 3" class="more">ещё {{ day.totalCount - 3 }}</span></button></div><aside class="backlog-panel"><button class="backlog-toggle" @click="backlogOpen = !backlogOpen">{{ backlogOpen ? 'Свернуть' : 'Развернуть' }} · Бэклог месяца</button><template v-if="backlogOpen"><div v-if="backlog.length" class="backlog-list"><div v-for="item in backlog" :key="item.id" class="backlog-row"><strong>{{ item.deloTitle }}</strong><span>{{ item.plannedHours == null ? '—' : `${item.plannedHours} ч` }}</span></div></div><p v-else class="muted-block">Пусто</p></template></aside></div></template>
  </div>
</template>
<style scoped>.calendar-header,.calendar-controls,.month-toolbar{display:flex;align-items:center;gap:.75rem}.calendar-header,.month-toolbar{justify-content:space-between}.month-toolbar{margin:1rem 0}.month-grid{display:grid;grid-template-columns:repeat(7,1fr);border:1px solid #e6dfd4;border-radius:12px;overflow:hidden}.weekday{padding:.65rem;text-align:center;background:#f7f3ec;color:#756d64;font-size:.8rem}.month-day{min-height:8rem;padding:.55rem;text-align:left;border:0;border-top:1px solid #e6dfd4;border-right:1px solid #e6dfd4;background:#fdfbf7;display:grid;align-content:start;gap:.25rem;cursor:pointer}.month-day:nth-child(7n){border-right:0}.month-day.muted{opacity:.45}.month-day.today{box-shadow:inset 0 0 0 2px #8195ad}.month-entry{font-size:.72rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;color:#4f6075}.more{font-size:.72rem;color:#756d64}</style>
