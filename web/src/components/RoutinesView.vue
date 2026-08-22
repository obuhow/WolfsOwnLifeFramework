<script setup>
import { computed, onMounted, ref } from 'vue'
import { apiBase } from '../api'

const routines = ref([])
const goals = ref([])
const spheres = ref([])
const loading = ref(false)
const error = ref('')
const success = ref('')
const showForm = ref(false)
const editingId = ref(null)
const selectedId = ref(null)
const selectedGoalId = ref('')
const selectedSphereId = ref('')
const form = ref({ title: '', description: '', weeklyHours: '', color: '#6B7280', icon: '' })
const schedule = ref({ dayOfWeek: 'MONDAY', startTime: '', endTime: '' })
const days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

const selected = computed(() => routines.value.find(item => item.id === selectedId.value) || null)
const curve = ref([])
const curveForm = ref({ weekStart: '', hours: '' })

async function loadCurve() {
  if (!selected.value) { curve.value = []; return }
  try { curve.value = await request(`/routines/${selected.value.id}/load-curve`) || [] } catch (e) { error.value = e.message }
}
async function saveCurve() {
  if (!selected.value || !curveForm.value.weekStart || curveForm.value.hours === '') return
  try { await request(`/routines/${selected.value.id}/load-curve`, { method: 'PUT', body: JSON.stringify({ weekStart: curveForm.value.weekStart, hours: Number(curveForm.value.hours) }) }); curveForm.value = { weekStart: '', hours: '' }; await loadCurve(); success.value = 'Кривая нагрузки сохранена' } catch (e) { error.value = e.message }
}
async function deleteCurve(item) {
  try { await request(`/routines/${selected.value.id}/load-curve/${item.id}`, { method: 'DELETE' }); await loadCurve() } catch (e) { error.value = e.message }
}

function headers(json = false) {
  const token = localStorage.getItem('wolf_token')
  if (!token) { window.location.hash = '#/login'; return null }
  return { Authorization: `Bearer ${token}`, ...(json ? { 'Content-Type': 'application/json' } : {}) }
}

async function request(path, options = {}) {
  const result = await fetch(`${apiBase()}${path}`, { ...options, headers: { ...headers(Boolean(options.body)), ...(options.headers || {}) } })
  if (!result.ok) {
    const body = await result.json().catch(() => ({}))
    throw new Error(body.message || `HTTP ${result.status}`)
  }
  return result.status === 204 ? null : result.json()
}

async function load() {
  loading.value = true; error.value = ''
  try {
    const [routineData, goalData, sphereData] = await Promise.all([
      request('/routines'), request('/goals'), request('/life-spheres')
    ])
    routines.value = routineData || []
    goals.value = goalData || []
    spheres.value = sphereData || []
    if (selectedId.value && !selected.value) selectedId.value = null
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}

function selectRoutine(id) { selectedId.value = id; loadCurve() }

function openCreate() {
  editingId.value = null
  form.value = { title: '', description: '', weeklyHours: '', color: '#6B7280', icon: '' }
  showForm.value = true
}
function openEdit(item) {
  editingId.value = item.id
  selectedId.value = item.id
  form.value = { title: item.title, description: item.description || '', weeklyHours: item.weeklyHours, color: item.color || '#6B7280', icon: item.icon || '' }
  showForm.value = true
}
async function save() {
  if (!form.value.title.trim()) { error.value = 'Название обязательно'; return }
  try {
    const body = { ...form.value, title: form.value.title.trim(), weeklyHours: Number(form.value.weeklyHours) }
    const saved = await request(editingId.value ? `/routines/${editingId.value}` : '/routines', { method: editingId.value ? 'PUT' : 'POST', body: JSON.stringify(body) })
    showForm.value = false; selectedId.value = saved.id; success.value = 'Рутина сохранена'; await load()
  } catch (e) { error.value = e.message }
}
async function archive(item) {
  try { await request(`/routines/${item.id}/archive`, { method: 'POST' }); await load() }
  catch (e) { error.value = e.message }
}
async function addSchedule() {
  if (!selected.value || !schedule.value.startTime || !schedule.value.endTime) return
  try { await request(`/routines/${selected.value.id}/schedules`, { method: 'POST', body: JSON.stringify(schedule.value) }); schedule.value.startTime = ''; schedule.value.endTime = ''; await load() }
  catch (e) { error.value = e.message }
}
async function deleteSchedule(item) {
  try { await request(`/routines/${selected.value.id}/schedules/${item.id}`, { method: 'DELETE' }); await load() }
  catch (e) { error.value = e.message }
}
async function linkGoal() {
  if (!selected.value || !selectedGoalId.value) return
  try { await request(`/routines/${selected.value.id}/goals/${Number(selectedGoalId.value)}`, { method: 'POST' }); selectedGoalId.value = ''; await load() }
  catch (e) { error.value = e.message }
}
async function linkSphere() {
  if (!selected.value || !selectedSphereId.value || !spheres.value.length) return
  try { await request('/synergies', { method: 'POST', body: JSON.stringify({ routineId: selected.value.id, sphereId: Number(selectedSphereId.value), impact: 'POSITIVE' }) }); selectedSphereId.value = ''; success.value = 'Связь со сферой добавлена'; await load() }
  catch (e) { error.value = e.message }
}

onMounted(load)
</script>

<template>
  <section class="page routines-page">
    <header class="page-header">
      <div><p class="eyebrow">Управление потоком</p><h1>Рутины</h1><p class="page-intro">Постоянные процессы с недельной квотой и повторяющимся расписанием.</p></div>
      <button class="btn btn-primary" @click="openCreate">Добавить рутину</button>
    </header>
    <p v-if="error" class="notice notice-error">{{ error }}</p>
    <p v-if="success" class="notice notice-success">{{ success }}</p>
    <div v-if="loading" class="empty-state">Загрузка…</div>
    <div v-else-if="!routines.length" class="empty-state">Пока нет рутин.</div>
    <div v-else class="routine-list">
      <article v-for="item in routines" :key="item.id" class="routine-row" :class="{ selected: selectedId === item.id, archived: item.archived }" @click="selectRoutine(item.id)">
        <span class="routine-icon">{{ item.icon || '○' }}</span><div><h2>{{ item.title }}</h2><p>{{ item.description || 'Без описания' }}</p></div><strong>{{ item.weeklyHours }} ч/нед</strong><button class="text-button" @click.stop="openEdit(item)">Изменить</button><button class="text-button" @click.stop="archive(item)">{{ item.archived ? 'Вернуть' : 'Архив' }}</button>
      </article>
    </div>

    <section v-if="selected" class="routine-detail">
      <h2>{{ selected.title }}</h2>
      <div class="curve-editor">
        <h3>Кривая нагрузки</h3><p class="muted">Одна строка = часы этой Рутины на ISO-неделю. Без строки действует недельная квота.</p>
        <div class="inline-form"><input v-model="curveForm.weekStart" type="date"><input v-model="curveForm.hours" type="number" min="0" step="0.25" placeholder="ч/нед"><button class="btn btn-primary" @click="saveCurve">Добавить неделю</button></div>
        <ul class="plain-list"><li v-for="item in curve" :key="item.id"><span>{{ item.weekStart }} · {{ item.hours }} ч/нед</span><button class="text-button" @click="deleteCurve(item)">Удалить</button></li><li v-if="!curve.length" class="muted">Переменная огибающая не задана.</li></ul>
      </div>
      <div class="detail-grid">
        <div><h3>Расписание</h3><div class="inline-form"><select v-model="schedule.dayOfWeek"><option v-for="day in days" :key="day" :value="day">{{ day }}</option></select><input v-model="schedule.startTime" type="time"><input v-model="schedule.endTime" type="time"><button class="btn btn-primary" @click="addSchedule">Добавить</button></div><ul class="plain-list"><li v-for="item in selected.schedules" :key="item.id">{{ item.dayOfWeek }} · {{ item.startTime }}–{{ item.endTime }} <button class="text-button" @click="deleteSchedule(item)">Удалить</button></li><li v-if="!selected.schedules.length" class="muted">Расписание пока не задано.</li></ul></div>
        <div><h3>Цели</h3><div class="inline-form"><select v-model="selectedGoalId"><option value="">Выберите цель</option><option v-for="goal in goals.filter(goal => !selected.goalIds.includes(goal.id))" :key="goal.id" :value="goal.id">{{ goal.title }}</option></select><button class="btn btn-primary" @click="linkGoal">Связать</button></div><ul class="plain-list"><li v-for="goal in selected.goals" :key="goal.goalId">{{ goal.goalTitle }}</li><li v-if="!selected.goals.length" class="muted">Цели ещё не связаны.</li></ul></div>
        <div><h3>Сферы жизни</h3><div class="inline-form"><select v-model="selectedSphereId"><option value="">Выберите сферу</option><option v-for="sphere in spheres" :key="sphere.id" :value="sphere.id">{{ sphere.name }}</option></select><button class="btn btn-primary" @click="linkSphere">Связать</button></div><p class="muted">Связи Рутины со Сферами жизни сохраняются как Синергия.</p></div>
      </div>
    </section>

    <div v-if="showForm" class="dialog-backdrop" @click.self="showForm = false"><form class="dialog" @submit.prevent="save"><div class="dialog-header"><h2>{{ editingId ? 'Изменить рутину' : 'Новая рутина' }}</h2><button type="button" class="icon-button" @click="showForm = false">×</button></div><label>Название<input v-model="form.title" required maxlength="200"></label><label>Описание<textarea v-model="form.description" rows="3"></textarea></label><label>Недельная квота, ч<input v-model="form.weeklyHours" type="number" min="0" step="0.25" required></label><label>Цвет<input v-model="form.color" maxlength="7"></label><label>Иконка<input v-model="form.icon" maxlength="50" placeholder="moon"></label><div class="dialog-actions"><button type="button" class="btn btn-ghost" @click="showForm = false">Отмена</button><button class="btn btn-primary">Сохранить</button></div></form></div>
  </section>
</template>

<style scoped>
.routines-page { max-width: 1100px; margin: 0 auto; padding: 42px 28px 80px; }
.page-header { display:flex; align-items:flex-start; justify-content:space-between; gap:24px; border-bottom:1px solid var(--wolf-ink); padding-bottom:22px; margin-bottom:22px; }
.eyebrow { color:var(--wolf-muted); font-size:11px; letter-spacing:.12em; text-transform:uppercase; margin:0 0 8px; } h1 { margin:0; font-size:32px; font-weight:500; letter-spacing:-.03em; } .page-intro { color:var(--wolf-muted); margin:8px 0 0; }
.routine-list { border-top:1px solid var(--wolf-rule); } .routine-row { display:grid; grid-template-columns:40px 1fr auto auto auto; gap:16px; align-items:center; padding:18px 0; border-bottom:1px solid var(--wolf-rule); cursor:pointer; } .routine-row.selected { background:var(--wolf-hover); } .routine-row.archived { opacity:.55; } .routine-row h2 { margin:0; font-size:17px; font-weight:500; } .routine-row p { color:var(--wolf-muted); margin:4px 0 0; } .routine-icon { color:var(--wolf-muted); text-align:center; } .routine-row strong { font-weight:500; white-space:nowrap; } .text-button { border:0; background:none; padding:0; color:var(--wolf-muted); cursor:pointer; } .curve-editor { margin: 18px 0 28px; border-top: 1px solid var(--wolf-rule); padding-top: 16px; }
.routine-detail { margin-top:42px; border-top:1px solid var(--wolf-ink); padding-top:20px; } .detail-grid { display:grid; grid-template-columns:repeat(3, 1fr); gap:28px; margin-top:22px; } h3 { font-size:15px; font-weight:500; } .inline-form { display:flex; gap:8px; align-items:end; flex-wrap:wrap; } .inline-form select, .inline-form input { min-width:0; flex:1; } .plain-list { list-style:none; padding:0; border-top:1px solid var(--wolf-rule); margin-top:18px; } .plain-list li { display:flex; justify-content:space-between; gap:10px; padding:10px 0; border-bottom:1px solid var(--wolf-subrule); } .muted, .empty-state { color:var(--wolf-muted); } .empty-state { padding:40px 0; } .btn { border:0; background:none; padding:7px 0; cursor:pointer; } .btn-primary { border-bottom:1px solid currentColor; } .btn-ghost { color:var(--wolf-muted); } .notice { padding:10px 0; } .notice-error { color:#8b3c35; } .notice-success { color:var(--wolf-done-ink); } .dialog-backdrop { position:fixed; inset:0; background:rgba(255,255,255,.82); display:grid; place-items:center; z-index:200; } .dialog { width:min(480px, calc(100vw - 32px)); background:#fff; border:1px solid var(--wolf-ink); padding:24px; display:grid; gap:16px; } .dialog-header { display:flex; justify-content:space-between; } .dialog label { display:grid; gap:5px; color:var(--wolf-muted); font-size:12px; } .dialog input, .dialog textarea { border:0; border-bottom:1px solid var(--wolf-rule); padding:8px 2px; background:transparent; } .dialog-actions { display:flex; justify-content:flex-end; gap:20px; } .icon-button { border:0; background:none; font-size:24px; cursor:pointer; }
@media (max-width:760px) { .routines-page { padding:28px 16px 60px; } .page-header { display:block; } .page-header .btn { margin-top:18px; } .routine-row { grid-template-columns:28px 1fr auto; } .routine-row .text-button { display:none; } .detail-grid { grid-template-columns:1fr; } }
</style>
