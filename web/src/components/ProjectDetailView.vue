<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiBase } from '../api'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const error = ref('')
const success = ref('')
const areas = ref([])
const projects = ref([])
const detail = ref(null)
const editing = ref(false)

const form = ref({
  lifeAreaId: '',
  parentId: '',
  title: '',
  description: '',
  startDate: '',
  endDate: '',
  totalPlanHours: ''
})

const projectId = computed(() => Number(route.params.id))

const parentOptions = computed(() => {
  const areaId = form.value.lifeAreaId ? Number(form.value.lifeAreaId) : null
  return projects.value
    .filter(p => {
      if (p.id === projectId.value) return false
      if (areaId && p.lifeAreaId !== areaId) return false
      if (isDescendantOf(p.id, projectId.value)) return false
      return true
    })
    .map(p => ({ id: p.id, label: p.title }))
})

const children = computed(() =>
  projects.value
    .filter(p => p.parentId === projectId.value)
    .sort((a, b) => a.title.localeCompare(b.title, 'ru'))
)

function authHeaders(json = false) {
  const token = localStorage.getItem('wolf_token')
  if (!token) {
    window.location.href = '#/login'
    return null
  }
  const headers = { Authorization: `Bearer ${token}` }
  if (json) headers['Content-Type'] = 'application/json'
  return headers
}

function isDescendantOf(candidateId, ancestorId) {
  let cursor = projects.value.find(p => p.id === candidateId)
  const guard = new Set()
  while (cursor && cursor.parentId != null) {
    if (cursor.parentId === ancestorId) return true
    if (guard.has(cursor.parentId)) break
    guard.add(cursor.parentId)
    cursor = projects.value.find(p => p.id === cursor.parentId)
  }
  return false
}

function fillFormFromDetail() {
  if (!detail.value) return
  form.value = {
    lifeAreaId: String(detail.value.lifeAreaId),
    parentId: detail.value.parentId != null ? String(detail.value.parentId) : '',
    title: detail.value.title || '',
    description: detail.value.description || '',
    startDate: detail.value.startDate || '',
    endDate: detail.value.endDate || '',
    totalPlanHours: detail.value.totalPlanHours != null ? String(detail.value.totalPlanHours) : ''
  }
}

async function loadAreas() {
  const headers = authHeaders()
  if (!headers) return
  const res = await fetch(`${apiBase()}/life-areas`, { headers })
  if (!res.ok) throw new Error(`Области: HTTP ${res.status}`)
  areas.value = await res.json()
}

async function loadProjects() {
  const headers = authHeaders()
  if (!headers) return
  const res = await fetch(`${apiBase()}/projects`, { headers })
  if (!res.ok) throw new Error(`Проекты: HTTP ${res.status}`)
  projects.value = await res.json()
}

async function loadDetail() {
  const headers = authHeaders()
  if (!headers) return
  const res = await fetch(`${apiBase()}/projects/${projectId.value}`, { headers })
  if (!res.ok) {
    const data = await res.json().catch(() => ({}))
    throw new Error(data.message || `HTTP ${res.status}`)
  }
  detail.value = await res.json()
  fillFormFromDetail()
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    await Promise.all([loadAreas(), loadProjects()])
    await loadDetail()
    editing.value = false
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
    detail.value = null
  } finally {
    loading.value = false
  }
}

function startEdit() {
  fillFormFromDetail()
  editing.value = true
}

function cancelEdit() {
  fillFormFromDetail()
  editing.value = false
  error.value = ''
}

function payloadFromForm() {
  const hoursRaw = form.value.totalPlanHours.trim()
  return {
    lifeAreaId: Number(form.value.lifeAreaId),
    parentId: form.value.parentId ? Number(form.value.parentId) : null,
    title: form.value.title.trim(),
    description: form.value.description.trim() || null,
    startDate: form.value.startDate || null,
    endDate: form.value.endDate || null,
    totalPlanHours: hoursRaw === '' ? null : Number(hoursRaw)
  }
}

async function save() {
  if (!form.value.title.trim()) {
    error.value = 'Название обязательно'
    return
  }
  loading.value = true
  error.value = ''
  success.value = ''
  try {
    const headers = authHeaders(true)
    if (!headers) return
    const res = await fetch(`${apiBase()}/projects/${projectId.value}`, {
      method: 'PUT',
      headers,
      body: JSON.stringify(payloadFromForm())
    })
    if (!res.ok) {
      const data = await res.json().catch(() => ({}))
      throw new Error(data.message || `HTTP ${res.status}`)
    }
    success.value = 'Сохранено'
    editing.value = false
    await loadAll()
    setTimeout(() => { success.value = '' }, 3000)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function remove() {
  if (!detail.value) return
  if (!confirm(`Удалить проект «${detail.value.title}» и все подпроекты?`)) return
  loading.value = true
  error.value = ''
  try {
    const headers = authHeaders()
    if (!headers) return
    const res = await fetch(`${apiBase()}/projects/${projectId.value}`, {
      method: 'DELETE',
      headers
    })
    if (!res.ok) {
      const data = await res.json().catch(() => ({}))
      throw new Error(data.message || `HTTP ${res.status}`)
    }
    router.push('/projects')
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
    loading.value = false
  }
}

watch(() => route.params.id, () => {
  if (route.params.id) loadAll()
})

onMounted(loadAll)
</script>

<template>
  <div class="project-page">
    <header class="page-header">
      <p class="eyebrow">
        <router-link to="/projects" class="crumb">← Все проекты</router-link>
      </p>
      <h1 v-if="detail">{{ detail.title }}</h1>
      <h1 v-else>Проект</h1>
      <p v-if="detail" class="eyebrow">{{ detail.lifeAreaName }}</p>
    </header>

    <div v-if="error" class="alert alert-error" style="margin-bottom: 1.5rem">{{ error }}</div>
    <div v-if="success" class="alert alert-success" style="margin-bottom: 1.5rem">{{ success }}</div>
    <div v-if="loading && !detail" class="loading">Загрузка…</div>

    <template v-if="detail">
      <section class="card" style="margin-bottom: 1.5rem">
        <div class="projects-toolbar" style="margin-bottom: 1rem">
          <h2 style="margin: 0">Карточка</h2>
          <div class="projects-toolbar-actions">
            <button v-if="!editing" class="btn btn-primary" :disabled="loading" @click="startEdit">Изменить</button>
            <button v-if="!editing" class="btn btn-ghost" :disabled="loading" style="color: #8a3a3a" @click="remove">Удалить</button>
          </div>
        </div>

        <form v-if="editing" class="project-form" @submit.prevent="save">
          <div class="form-row">
            <div class="form-group">
              <label for="detail-area">Область жизни</label>
              <select id="detail-area" v-model="form.lifeAreaId" class="input" :disabled="loading" required>
                <option v-for="a in areas" :key="a.id" :value="String(a.id)">{{ a.name }}</option>
              </select>
            </div>
            <div class="form-group">
              <label for="detail-parent">Родитель</label>
              <select id="detail-parent" v-model="form.parentId" class="input" :disabled="loading">
                <option value="">— без родителя —</option>
                <option v-for="p in parentOptions" :key="p.id" :value="String(p.id)">{{ p.label }}</option>
              </select>
            </div>
          </div>

          <div class="form-group">
            <label for="detail-title">Название</label>
            <input id="detail-title" v-model="form.title" type="text" class="input" maxlength="200" required :disabled="loading" />
          </div>

          <div class="form-group">
            <label for="detail-desc">Описание</label>
            <textarea id="detail-desc" v-model="form.description" class="input textarea" rows="4" maxlength="10000" :disabled="loading" />
          </div>

          <div class="form-row">
            <div class="form-group">
              <label for="detail-start">Начало</label>
              <input id="detail-start" v-model="form.startDate" type="date" class="input" :disabled="loading" />
            </div>
            <div class="form-group">
              <label for="detail-end">Окончание</label>
              <input id="detail-end" v-model="form.endDate" type="date" class="input" :disabled="loading" />
            </div>
            <div class="form-group">
              <label for="detail-hours">План, часы</label>
              <input id="detail-hours" v-model="form.totalPlanHours" type="number" min="0" step="0.25" class="input" :disabled="loading" />
            </div>
          </div>

          <div class="form-actions">
            <button type="submit" class="btn btn-primary" :disabled="loading">Сохранить</button>
            <button type="button" class="btn btn-ghost" :disabled="loading" @click="cancelEdit">Отмена</button>
          </div>
        </form>

        <dl v-else class="detail-grid">
          <div>
            <dt>Область жизни</dt>
            <dd>{{ detail.lifeAreaName }}</dd>
          </div>
          <div>
            <dt>Родитель</dt>
            <dd>
              <router-link v-if="detail.parentId" :to="`/projects/${detail.parentId}`">{{ detail.parentTitle }}</router-link>
              <span v-else class="muted">— корневой —</span>
            </dd>
          </div>
          <div>
            <dt>Сроки</dt>
            <dd>
              <span v-if="detail.startDate || detail.endDate">
                {{ detail.startDate || '…' }} — {{ detail.endDate || '…' }}
              </span>
              <span v-else class="muted">не заданы</span>
            </dd>
          </div>
          <div>
            <dt>Плановые часы</dt>
            <dd>
              <span v-if="detail.totalPlanHours != null">{{ detail.totalPlanHours }} ч</span>
              <span v-else class="muted">не заданы</span>
            </dd>
          </div>
          <div class="detail-span">
            <dt>Описание</dt>
            <dd class="preserve-lines">{{ detail.description || '—' }}</dd>
          </div>
        </dl>
      </section>

      <section class="card" style="margin-bottom: 1.5rem">
        <h2>Подпроекты</h2>
        <div v-if="children.length === 0" class="muted-block">Пока нет вложенных проектов.</div>
        <ul v-else class="child-list">
          <li v-for="c in children" :key="c.id">
            <router-link :to="`/projects/${c.id}`">{{ c.title }}</router-link>
          </li>
        </ul>
      </section>

      <section class="card" style="margin-bottom: 1.5rem">
        <h2>Связанные Дела</h2>
        <div class="muted-block">
          Прикрепление Дел появится в следующем шаге (тикет 06). Место уже есть — список пока пуст.
        </div>
        <ul v-if="detail.delos && detail.delos.length" class="child-list">
          <li v-for="(d, i) in detail.delos" :key="i">{{ d }}</li>
        </ul>
      </section>

      <section class="card">
        <h2>Агрегаты времени</h2>
        <div class="muted-block">
          Сводка по факту часов появится позже (тикет 13). Здесь будет спокойный блок «сколько уже вложено».
        </div>
      </section>
    </template>
  </div>
</template>