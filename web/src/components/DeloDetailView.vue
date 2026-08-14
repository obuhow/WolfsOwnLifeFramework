<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiBase } from '../api'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const error = ref('')
const success = ref('')
const projects = ref([])
const detail = ref(null)
const editing = ref(false)
const selectedProjectId = ref('')

const form = ref({
  title: '',
  description: '',
  executionMode: 'SELF',
  projectIds: [],
  primaryProjectId: ''
})

const deloId = computed(() => Number(route.params.id))

const executionModes = [
  { value: 'SELF', label: 'Сам' },
  { value: 'DELEGATABLE', label: 'Можно делегировать' },
  { value: 'AUTOMATABLE', label: 'Можно автоматизировать' }
]

const availableProjects = computed(() => {
  const linked = new Set(detail.value?.projects?.map(p => p.id) || [])
  return projects.value
    .filter(p => !linked.has(p.id))
    .slice()
    .sort((a, b) => a.title.localeCompare(b.title, 'ru'))
})

const aggregates = computed(() => detail.value?.aggregates || null)
const aggregateDays = computed(() => aggregates.value?.byDay || [])
const totalFactHours = computed(() => {
  const v = aggregates.value?.totalFactHours
  if (v == null) return '0'
  return formatHours(v)
})

function formatHours(v) {
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return Number.isInteger(n) ? String(n) : n.toFixed(2).replace(/\.?0+$/, '')
}

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

function modeLabel(mode) {
  return executionModes.find(m => m.value === mode)?.label || mode
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
  const res = await fetch(`${apiBase()}/delos/${deloId.value}`, { headers })
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
    await Promise.all([loadProjects(), loadDetail()])
    editing.value = false
    selectedProjectId.value = ''
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
    detail.value = null
  } finally {
    loading.value = false
  }
}

function fillFormFromDetail() {
  if (!detail.value) return
  form.value = {
    title: detail.value.title || '',
    description: detail.value.description || '',
    executionMode: detail.value.executionMode || 'SELF',
    projectIds: (detail.value.projects || []).map(p => String(p.id)),
    primaryProjectId: detail.value.projects?.find(p => p.isPrimary)?.id != null
      ? String(detail.value.projects.find(p => p.isPrimary).id)
      : ''
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

function onProjectsChange() {
  if (form.value.primaryProjectId && !form.value.projectIds.includes(form.value.primaryProjectId)) {
    form.value.primaryProjectId = form.value.projectIds[0] || ''
  }
  if (!form.value.primaryProjectId && form.value.projectIds.length) {
    form.value.primaryProjectId = form.value.projectIds[0]
  }
}

function projectTitle(id) {
  const fromDetail = detail.value?.projects?.find(p => p.id === Number(id))
  if (fromDetail) return fromDetail.title
  return projects.value.find(p => p.id === Number(id))?.title || `#${id}`
}

function payloadFromForm() {
  const projectIds = form.value.projectIds.map(Number).filter(Boolean)
  return {
    title: form.value.title.trim(),
    description: form.value.description.trim() || null,
    executionMode: form.value.executionMode,
    projectIds,
    primaryProjectId: form.value.primaryProjectId ? Number(form.value.primaryProjectId) : null
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
    const res = await fetch(`${apiBase()}/delos/${deloId.value}`, {
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
  if (!confirm(`Удалить Дело «${detail.value.title}»?`)) return
  loading.value = true
  error.value = ''
  try {
    const headers = authHeaders()
    if (!headers) return
    const res = await fetch(`${apiBase()}/delos/${deloId.value}`, {
      method: 'DELETE',
      headers
    })
    if (!res.ok) {
      const data = await res.json().catch(() => ({}))
      throw new Error(data.message || `HTTP ${res.status}`)
    }
    router.push('/delos')
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
    loading.value = false
  }
}

async function linkProject() {
  if (!selectedProjectId.value) return
  loading.value = true
  error.value = ''
  try {
    const headers = authHeaders()
    if (!headers) return
    const res = await fetch(`${apiBase()}/delos/${deloId.value}/link/${selectedProjectId.value}`, {
      method: 'POST',
      headers
    })
    if (!res.ok) {
      const data = await res.json().catch(() => ({}))
      throw new Error(data.message || `HTTP ${res.status}`)
    }
    selectedProjectId.value = ''
    await loadDetail()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function unlinkProject(projectId) {
  loading.value = true
  error.value = ''
  try {
    const headers = authHeaders()
    if (!headers) return
    const res = await fetch(`${apiBase()}/delos/${deloId.value}/link/${projectId}`, {
      method: 'DELETE',
      headers
    })
    if (!res.ok) {
      const data = await res.json().catch(() => ({}))
      throw new Error(data.message || `HTTP ${res.status}`)
    }
    await loadDetail()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function setPrimary(projectId) {
  loading.value = true
  error.value = ''
  try {
    const headers = authHeaders()
    if (!headers) return
    const res = await fetch(`${apiBase()}/delos/${deloId.value}/primary/${projectId}`, {
      method: 'PUT',
      headers
    })
    if (!res.ok) {
      const data = await res.json().catch(() => ({}))
      throw new Error(data.message || `HTTP ${res.status}`)
    }
    await loadDetail()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
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
        <router-link to="/delos" class="crumb">← Все Дела</router-link>
      </p>
      <h1 v-if="detail">{{ detail.title }}</h1>
      <h1 v-else>Дело</h1>
    </header>

    <div v-if="error" class="alert alert-error" style="margin-bottom: 1.5rem">{{ error }}</div>
    <div v-if="success" class="alert alert-success" style="margin-bottom: 1.5rem">{{ success }}</div>
    <div v-if="loading && !detail" class="loading">Загрузка…</div>

    <template v-if="detail">
      <section class="card" style="margin-bottom: 1.5rem">
        <div class="projects-toolbar" style="margin-bottom: 1rem">
          <h2 style="margin: 0">Карточка Дела</h2>
          <div class="projects-toolbar-actions">
            <button v-if="!editing" class="btn btn-primary" :disabled="loading" @click="startEdit">Изменить</button>
            <button v-if="!editing" class="btn btn-ghost" :disabled="loading" style="color: #8a3a3a" @click="remove">Удалить</button>
          </div>
        </div>

        <form v-if="editing" class="project-form" @submit.prevent="save">
          <div class="form-group">
            <label for="delo-title">Название</label>
            <input id="delo-title" v-model="form.title" type="text" class="input" maxlength="200" required :disabled="loading" />
          </div>

          <div class="form-group">
            <label for="delo-desc">Описание</label>
            <textarea id="delo-desc" v-model="form.description" class="input textarea" rows="4" maxlength="10000" :disabled="loading" />
          </div>

          <div class="form-row">
            <div class="form-group">
              <label for="delo-mode">Способ исполнения</label>
              <select id="delo-mode" v-model="form.executionMode" class="input" :disabled="loading">
                <option v-for="m in executionModes" :key="m.value" :value="m.value">{{ m.label }}</option>
              </select>
            </div>
            <div class="form-group">
              <label for="delo-projects">Проекты</label>
              <select
                id="delo-projects"
                v-model="form.projectIds"
                class="input"
                multiple
                size="4"
                :disabled="loading"
                @change="onProjectsChange"
              >
                <option v-for="p in projects" :key="p.id" :value="String(p.id)">{{ p.title }}</option>
              </select>
            </div>
            <div class="form-group">
              <label for="delo-primary">Основной проект</label>
              <select
                id="delo-primary"
                v-model="form.primaryProjectId"
                class="input"
                :disabled="loading || form.projectIds.length === 0"
              >
                <option value="">— нет —</option>
                <option v-for="pid in form.projectIds" :key="pid" :value="pid">{{ projectTitle(pid) }}</option>
              </select>
            </div>
          </div>

          <div class="form-actions">
            <button type="submit" class="btn btn-primary" :disabled="loading">Сохранить</button>
            <button type="button" class="btn btn-ghost" :disabled="loading" @click="cancelEdit">Отмена</button>
          </div>
        </form>

        <dl v-else class="detail-grid">
          <div>
            <dt>Способ исполнения</dt>
            <dd>{{ modeLabel(detail.executionMode) }}</dd>
          </div>
          <div class="detail-span">
            <dt>Описание</dt>
            <dd class="preserve-lines">{{ detail.description || '—' }}</dd>
          </div>
        </dl>
      </section>

      <section class="card" style="margin-bottom: 1.5rem">
        <div class="projects-toolbar" style="margin-bottom: 1rem">
          <h2 style="margin: 0">Проекты</h2>
          <div v-if="!editing" class="projects-toolbar-actions">
            <select v-model="selectedProjectId" class="input filter-select" :disabled="loading || availableProjects.length === 0">
              <option value="">— выбрать проект —</option>
              <option v-for="p in availableProjects" :key="p.id" :value="String(p.id)">{{ p.title }}</option>
            </select>
            <button class="btn btn-primary" :disabled="loading || !selectedProjectId" @click="linkProject">
              Прикрепить
            </button>
          </div>
        </div>

        <div v-if="detail.projects && detail.projects.length" class="link-list">
          <div v-for="p in detail.projects" :key="p.id" class="project-link-row">
            <div class="project-link-main">
              <router-link :to="`/projects/${p.id}`">{{ p.title }}</router-link>
              <span v-if="p.isPrimary" class="primary-badge">основной</span>
            </div>
            <div class="project-link-actions">
              <button
                v-if="!p.isPrimary"
                class="btn btn-ghost"
                :disabled="loading"
                @click="setPrimary(p.id)"
              >
                Основной
              </button>
              <button
                class="btn btn-ghost"
                style="color: #8a3a3a"
                :disabled="loading"
                @click="unlinkProject(p.id)"
              >
                Открепить
              </button>
            </div>
          </div>
        </div>
        <div v-else class="muted-block">Дело без проекта — валидная рутина/личное. Можно прикрепить позже.</div>
      </section>

      <section class="card">
        <div class="projects-toolbar" style="margin-bottom: 1rem">
          <h2 style="margin: 0">Фактические часы</h2>
          <div v-if="aggregates" class="aggregate-total">
            <span class="aggregate-total-value">{{ totalFactHours }} ч</span>
            <span class="muted">факт</span>
          </div>
        </div>

        <p v-if="aggregates" class="aggregate-mode muted">
          Сумма выполненных Записей времени по этому Делу (без разбиения по проектам).
        </p>

        <div v-if="aggregateDays.length" class="aggregate-table-wrap">
          <table class="aggregate-table">
            <thead>
              <tr>
                <th>День</th>
                <th class="num">Часы</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in aggregateDays" :key="row.date">
                <td>{{ row.date }}</td>
                <td class="num">{{ formatHours(row.hours) }}</td>
              </tr>
            </tbody>
            <tfoot>
              <tr>
                <td>Всего</td>
                <td class="num">{{ totalFactHours }}</td>
              </tr>
            </tfoot>
          </table>
        </div>
        <div v-else class="muted-block">
          Пока нет учтённых часов по этому Делу.
        </div>
      </section>
    </template>
  </div>
</template>
