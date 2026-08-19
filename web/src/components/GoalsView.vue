<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiBase } from '../api'

const route = useRoute()
const router = useRouter()
const goals = ref([])
const projects = ref([])
const selected = ref(null)
const loading = ref(false)
const error = ref('')
const success = ref('')
const showForm = ref(false)
const form = ref({ title: '', description: '', priority: '' })
const budget = ref({ week: currentWeek(), hours: '' })
const metric = ref({ kind: '', value: '', at: '' })
const selectedProjectId = ref('')

const selectedId = computed(() => route.params.id ? Number(route.params.id) : null)
const availableProjects = computed(() => {
  const linked = new Set(selected.value?.projects?.map(p => p.id) || [])
  return projects.value.filter(p => !linked.has(p.id)).sort((a, b) => a.title.localeCompare(b.title, 'ru'))
})

function currentWeek() {
  const d = new Date()
  const thursday = new Date(d)
  thursday.setDate(d.getDate() + 4 - (d.getDay() || 7))
  const yearStart = new Date(thursday.getFullYear(), 0, 1)
  const week = Math.ceil((((thursday - yearStart) / 86400000) + 1) / 7)
  return `${thursday.getFullYear()}-W${String(week).padStart(2, '0')}`
}

function headers(json = false) {
  const token = localStorage.getItem('wolf_token')
  if (!token) { router.push('/login'); return null }
  return { Authorization: `Bearer ${token}`, ...(json ? { 'Content-Type': 'application/json' } : {}) }
}

async function request(path, options = {}) {
  const h = headers(Boolean(options.body))
  if (!h) return null
  const res = await fetch(`${apiBase()}${path}`, { ...options, headers: { ...h, ...(options.headers || {}) } })
  if (!res.ok) {
    const data = await res.json().catch(() => ({}))
    throw new Error(data.message || `HTTP ${res.status}`)
  }
  return res.status === 204 ? null : res.json()
}

async function loadGoals() {
  goals.value = await request('/goals') || []
}
async function loadProjects() {
  projects.value = await request('/projects') || []
}
async function loadDetail() {
  selected.value = selectedId.value ? await request(`/goals/${selectedId.value}?week=${budget.value.week}`) : null
}
async function loadAll() {
  loading.value = true; error.value = ''
  try { await Promise.all([loadGoals(), loadProjects()]); await loadDetail() }
  catch (e) { error.value = e.message }
  finally { loading.value = false }
}

function openCreate() {
  form.value = { title: '', description: '', priority: String(goals.value.length + 1) }
  showForm.value = true
}
function openEdit() {
  form.value = { title: selected.value.goal.title, description: selected.value.goal.description || '', priority: String(selected.value.goal.priority) }
  showForm.value = true
}
function open(id) { router.push(`/goals/${id}`) }
function closeDetail() { router.push('/goals') }

async function saveGoal() {
  if (!form.value.title.trim()) { error.value = 'Название обязательно'; return }
  loading.value = true; error.value = ''
  try {
    const isEdit = Boolean(selected.value)
    const body = { title: form.value.title.trim(), description: form.value.description.trim() || null, priority: Number(form.value.priority) }
    await request(isEdit ? `/goals/${selectedId.value}` : '/goals', { method: isEdit ? 'PUT' : 'POST', body: JSON.stringify(body) })
    showForm.value = false; success.value = isEdit ? 'Цель обновлена' : 'Цель создан'
    await loadAll(); if (!isEdit && goals.value.length) open(goals.value.at(-1).id)
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}
async function reorder(id, priority) {
  try { await request(`/goals/${id}/priority`, { method: 'PUT', body: JSON.stringify({ priority: Number(priority) }) }); await loadAll() }
  catch (e) { error.value = e.message }
}
async function saveBudget() {
  try { await request(`/goals/${selectedId.value}/budget`, { method: 'POST', body: JSON.stringify({ week: budget.value.week, hours: Number(budget.value.hours) }) }); await loadDetail(); success.value = 'Бюджет сохранён' }
  catch (e) { error.value = e.message }
}
async function addMetric() {
  try { await request(`/goals/${selectedId.value}/metrics`, { method: 'POST', body: JSON.stringify({ kind: metric.value.kind, value: Number(metric.value.value), at: metric.value.at ? `${metric.value.at}T12:00:00` : null }) }); metric.value = { kind: '', value: '', at: '' }; await loadDetail() }
  catch (e) { error.value = e.message }
}
async function linkProject() {
  if (!selectedProjectId.value) return
  try { await request(`/goals/${selectedId.value}/projects/${selectedProjectId.value}`, { method: 'POST' }); selectedProjectId.value = ''; await loadDetail() }
  catch (e) { error.value = e.message }
}
async function unlinkProject(id) {
  try { await request(`/goals/${selectedId.value}/projects/${id}`, { method: 'DELETE' }); await loadDetail() }
  catch (e) { error.value = e.message }
}
async function archive() {
  try { await request(`/goals/${selectedId.value}/archive`, { method: 'POST' }); closeDetail(); await loadAll() }
  catch (e) { error.value = e.message }
}

onMounted(loadAll)
</script>

<template>
  <section class="page goals-page">
    <div class="page-header">
      <div><p class="eyebrow">Управление потоком</p><h1>Цели</h1><p class="page-intro">Векторы на ближайшие месяцы. Бюджет можно менять под реальность.</p></div>
      <button class="btn btn-primary" @click="openCreate">Добавить цель</button>
    </div>
    <p v-if="error" class="notice notice-error">{{ error }}</p>
    <p v-if="success" class="notice notice-success">{{ success }}</p>
    <div v-if="loading" class="empty-state">Загрузка…</div>
    <div v-else-if="!goals.length" class="empty-state">Пока нет целей. Добавьте первый вектор.</div>
    <div v-else class="goals-list">
      <article v-for="goal in goals" :key="goal.id" class="goal-row" @click="open(goal.id)">
        <div class="goal-priority"><label :for="`priority-${goal.id}`">№</label><input :id="`priority-${goal.id}`" :value="goal.priority" type="number" min="1" @click.stop @change="reorder(goal.id, $event.target.value)"></div>
        <div class="goal-main"><h2>{{ goal.title }}</h2><p v-if="goal.description">{{ goal.description }}</p></div>
        <span class="goal-arrow">→</span>
      </article>
    </div>

    <div v-if="showForm" class="dialog-backdrop" @click.self="showForm = false">
      <form class="dialog" @submit.prevent="saveGoal"><div class="dialog-header"><h2>{{ selected ? 'Изменить цель' : 'Новая цель' }}</h2><button type="button" class="icon-button" @click="showForm = false">×</button></div>
        <label>Название<input v-model="form.title" required maxlength="200"></label>
        <label>Описание<textarea v-model="form.description" rows="3"></textarea></label>
        <label>Приоритет<input v-model="form.priority" type="number" min="1" required></label>
        <div class="dialog-actions"><button type="button" class="btn btn-ghost" @click="showForm = false">Отмена</button><button class="btn btn-primary">Сохранить</button></div>
      </form>
    </div>

    <div v-if="selected" class="goal-detail">
      <div class="detail-header"><button class="back-link" @click="closeDetail">← Все цели</button><div class="detail-actions"><button class="btn btn-ghost" @click="openEdit">Изменить</button><button class="btn btn-ghost" @click="archive">Архивировать</button></div><h1>{{ selected.goal.title }}</h1><p v-if="selected.goal.description" class="page-intro">{{ selected.goal.description }}</p></div>
      <div class="goal-grid">
        <section class="detail-section"><h2>Неделя и факт</h2><div class="inline-form"><label>Неделя<input v-model="budget.week" @change="loadDetail"></label><label>Бюджет, ч<input v-model="budget.hours" type="number" min="0" step="0.25" placeholder="—"></label><button class="btn btn-primary" @click="saveBudget">Сохранить</button></div><div class="fact-line"><span>Факт за {{ selected.fact.week }}</span><strong>{{ selected.fact.hours }} ч</strong></div></section>
        <section class="detail-section"><h2>Метрики</h2><div class="inline-form"><label>Тип<input v-model="metric.kind" placeholder="вес кг / доход ₽"></label><label>Значение<input v-model="metric.value" type="number" step="any"></label><label>Дата<input v-model="metric.at" type="date"></label><button class="btn btn-primary" @click="addMetric">Добавить</button></div><ul class="history"><li v-for="item in selected.metrics" :key="item.id"><span>{{ item.kind }}</span><strong>{{ item.value }}</strong><time>{{ item.at?.slice(0, 10) }}</time></li><li v-if="!selected.metrics.length" class="muted">История пока пуста.</li></ul></section>
        <section class="detail-section"><h2>Связанные проекты</h2><div class="inline-form"><select v-model="selectedProjectId"><option value="">Выберите проект</option><option v-for="project in availableProjects" :key="project.id" :value="project.id">{{ project.title }}</option></select><button class="btn btn-primary" @click="linkProject">Связать</button></div><ul class="history"><li v-for="project in selected.projects" :key="project.id"><span>{{ project.title }}</span><button class="text-button" @click="unlinkProject(project.id)">Удалить связь</button></li><li v-if="!selected.projects.length" class="muted">Проекты ещё не связаны.</li></ul></section>
      </div>
    </div>
  </section>
</template>

<style scoped>
.goals-page { max-width: 1100px; margin: 0 auto; padding: 42px 28px 80px; }
.page-header, .detail-header { display:flex; align-items:flex-start; justify-content:space-between; gap:24px; border-bottom:1px solid var(--wolf-ink); padding-bottom:22px; margin-bottom:22px; }
.eyebrow { color:var(--wolf-muted); font-size:11px; letter-spacing:.12em; text-transform:uppercase; margin:0 0 8px; }
h1 { margin:0; font-size:32px; font-weight:500; letter-spacing:-.03em; } h2 { margin:0; font-size:18px; font-weight:500; }
.page-intro { color:var(--wolf-muted); margin:8px 0 0; max-width:620px; }
.goals-list { border-top:1px solid var(--wolf-rule); }
.goal-row { display:grid; grid-template-columns:100px 1fr 30px; gap:18px; align-items:center; padding:19px 0; border-bottom:1px solid var(--wolf-rule); cursor:pointer; } .goal-row:hover { background:var(--wolf-hover); }
.goal-priority { display:flex; align-items:center; gap:8px; color:var(--wolf-muted); } .goal-priority input { width:48px; border:0; border-bottom:1px solid var(--wolf-rule); background:transparent; padding:4px; }
.goal-main h2 { font-size:17px; } .goal-main p { color:var(--wolf-muted); margin:4px 0 0; } .goal-arrow { font-size:20px; color:var(--wolf-muted); }
.btn { border:0; background:none; padding:7px 0; cursor:pointer; } .btn-primary { border-bottom:1px solid currentColor; } .btn-ghost { color:var(--wolf-muted); }
.notice { padding:10px 0; margin:0 0 12px; } .notice-error { color:#8b3c35; } .notice-success { color:var(--wolf-done-ink); }
.empty-state { color:var(--wolf-muted); padding:40px 0; }
.dialog-backdrop { position:fixed; inset:0; background:rgba(255,255,255,.82); display:grid; place-items:center; z-index:200; } .dialog { width:min(480px, calc(100vw - 32px)); background:#fff; border:1px solid var(--wolf-ink); padding:24px; display:grid; gap:16px; } .dialog-header { display:flex; justify-content:space-between; } .dialog label, .inline-form label { display:grid; gap:5px; color:var(--wolf-muted); font-size:12px; } input, textarea, select { border:0; border-bottom:1px solid var(--wolf-rule); padding:8px 2px; background:transparent; color:var(--wolf-ink); min-width:0; } .dialog-actions { display:flex; justify-content:flex-end; gap:20px; margin-top:8px; } .icon-button { border:0; background:none; font-size:24px; cursor:pointer; }
.goal-detail { margin-top:46px; border-top:1px solid var(--wolf-ink); padding-top:24px; } .detail-header { display:grid; grid-template-columns:1fr auto; } .detail-header h1, .detail-header p { grid-column:1 / -1; } .back-link, .text-button { border:0; background:none; padding:0; color:var(--wolf-muted); cursor:pointer; } .detail-actions { display:flex; gap:18px; }
.goal-grid { display:grid; grid-template-columns:repeat(3, 1fr); gap:28px; } .detail-section { border-top:1px solid var(--wolf-rule); padding-top:14px; } .detail-section h2 { margin-bottom:16px; } .inline-form { display:flex; align-items:end; gap:12px; flex-wrap:wrap; } .inline-form label { flex:1 1 100px; } .fact-line { display:flex; justify-content:space-between; border-top:1px solid var(--wolf-rule); margin-top:20px; padding-top:14px; color:var(--wolf-muted); } .fact-line strong { color:var(--wolf-ink); font-size:22px; font-weight:500; }
.history { list-style:none; padding:0; margin:20px 0 0; border-top:1px solid var(--wolf-rule); } .history li { display:flex; justify-content:space-between; align-items:center; gap:12px; padding:10px 0; border-bottom:1px solid var(--wolf-subrule); } .history time, .muted { color:var(--wolf-muted); font-size:12px; }
@media (max-width: 760px) { .goals-page { padding:28px 16px 60px; } .page-header { display:block; } .page-header .btn { margin-top:18px; } .goal-row { grid-template-columns:70px 1fr 20px; } .goal-grid { grid-template-columns:1fr; gap:28px; } .detail-header { display:block; } .detail-actions { margin-top:16px; } }
</style>