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
import { computed, onMounted, ref } from 'vue'
import { apiBase, authHeaders } from '../api'

const ideas = ref([])
const areas = ref([])
const spheres = ref([])
const synergies = ref([])
const loading = ref(false)
const error = ref('')
const success = ref('')
const showForm = ref(false)
const editingId = ref(null)
const categoryFilter = ref('')
const statusFilter = ref('BANK')
const promoteIdea = ref(null)
const promoteLifeAreaId = ref('')
const synergyForms = ref({})

const categories = [
  { value: 'BUSINESS', label: 'Бизнес' },
  { value: 'MUSIC', label: 'Музыкальные' },
  { value: 'PERSONAL', label: 'Личные' },
  { value: 'CREEPY', label: 'Криповые' }
]
const statuses = [
  { value: 'BANK', label: 'В банке' },
  { value: 'IN_WORK', label: 'В работе' },
  { value: 'ARCHIVED', label: 'Архив' }
]

const form = ref({ title: '', description: '', category: 'PERSONAL', status: 'BANK' })
const visibleIdeas = computed(() => ideas.value)

function categoryLabel(value) {
  return categories.find(x => x.value === value)?.label || value
}
function statusLabel(value) {
  return statuses.find(x => x.value === value)?.label || value
}
function impactLabel(value) {
  return { POSITIVE: '+', NEGATIVE: '−', NEUTRAL: '0' }[value] || value
}
function ideaSynergies(ideaId) {
  return synergies.value.filter(item => item.ideaId === ideaId)
}
function availableSpheres(idea) {
  const used = new Set(ideaSynergies(idea.id).map(item => item.sphereId))
  return spheres.value.filter(sphere => !used.has(sphere.id))
}
function synergyFormFor(ideaId) {
  if (!synergyForms.value[ideaId]) {
    synergyForms.value[ideaId] = { sphereId: '', impact: 'POSITIVE' }
  }
  return synergyForms.value[ideaId]
}
function clearMessages() {
  error.value = ''
  success.value = ''
}
function openCreate() {
  editingId.value = null
  form.value = { title: '', description: '', category: 'PERSONAL', status: 'BANK' }
  clearMessages()
  showForm.value = true
}
function openEdit(idea) {
  editingId.value = idea.id
  form.value = {
    title: idea.title,
    description: idea.description || '',
    category: idea.category,
    status: idea.status
  }
  clearMessages()
  showForm.value = true
}
function cancelForm() {
  showForm.value = false
  editingId.value = null
}

async function request(url, options = {}) {
  const headers = authHeaders(Boolean(options.body))
  if (!headers) return null
  const res = await fetch(url, { ...options, headers: { ...headers, ...(options.headers || {}) } })
  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    throw new Error(body.message || `HTTP ${res.status}`)
  }
  return res.status === 204 ? null : res.json()
}

async function loadAll() {
  loading.value = true
  clearMessages()
  try {
    const params = new URLSearchParams()
    if (categoryFilter.value) params.set('category', categoryFilter.value)
    if (statusFilter.value) params.set('status', statusFilter.value)
    const [loadedIdeas, loadedAreas, loadedSpheres, loadedSynergies] = await Promise.all([
      request(`${apiBase()}/ideas?${params}`),
      areas.value.length ? Promise.resolve(areas.value) : request(`${apiBase()}/life-areas`),
      spheres.value.length ? Promise.resolve(spheres.value) : request(`${apiBase()}/life-spheres`),
      request(`${apiBase()}/synergies`)
    ])
    ideas.value = loadedIdeas
    areas.value = loadedAreas
    spheres.value = loadedSpheres
    synergies.value = loadedSynergies
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function saveIdea() {
  if (!form.value.title.trim()) {
    error.value = 'Название обязательно'
    return
  }
  loading.value = true
  clearMessages()
  try {
    const id = editingId.value
    const payload = {
      title: form.value.title.trim(),
      description: form.value.description.trim() || null,
      category: form.value.category,
      status: form.value.status
    }
    await request(id ? `${apiBase()}/ideas/${id}` : `${apiBase()}/ideas`, {
      method: id ? 'PUT' : 'POST',
      body: JSON.stringify(payload)
    })
    cancelForm()
    success.value = id ? 'Идея обновлена' : 'Идея сохранена в банк'
    await loadAll()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function addSynergy(idea) {
  const form = synergyFormFor(idea.id)
  if (!form.sphereId) return
  loading.value = true
  clearMessages()
  try {
    await request(`${apiBase()}/synergies`, {
      method: 'POST',
      body: JSON.stringify({
        ideaId: idea.id,
        sphereId: Number(form.sphereId),
        impact: form.impact
      })
    })
    form.sphereId = ''
    await loadAll()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function removeSynergy(synergy) {
  loading.value = true
  clearMessages()
  try {
    await request(`${apiBase()}/synergies/${synergy.id}`, { method: 'DELETE' })
    await loadAll()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

function openPromote(idea) {
  promoteIdea.value = idea
  promoteLifeAreaId.value = String(areas.value[0]?.id || '')
  clearMessages()
}
function cancelPromote() {
  promoteIdea.value = null
  promoteLifeAreaId.value = ''
}
async function promote() {
  if (!promoteLifeAreaId.value) {
    error.value = 'Выберите Область жизни для нового Проекта'
    return
  }
  loading.value = true
  clearMessages()
  try {
    const body = await request(`${apiBase()}/ideas/${promoteIdea.value.id}/promote`, {
      method: 'POST',
      body: JSON.stringify({ lifeAreaId: Number(promoteLifeAreaId.value) })
    })
    cancelPromote()
    success.value = 'Идея взята в работу как Проект'
    await loadAll()
    if (body?.projectId) window.location.hash = `#/projects/${body.projectId}`
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)
</script>

<template>
  <div class="ideas-page">
    <header class="page-header">
      <h1>Банк идей</h1>
      <p class="eyebrow">Место для «ещё не проектов» — без давления и обязательства начинать.</p>
    </header>

    <div v-if="error" class="alert alert-error" style="margin-bottom: 1.5rem">{{ error }}</div>
    <div v-if="success" class="alert alert-success" style="margin-bottom: 1.5rem">{{ success }}</div>

    <section v-if="showForm" class="card" style="margin-bottom: 1.5rem">
      <h2>{{ editingId ? 'Редактировать идею' : 'Новая идея' }}</h2>
      <form @submit.prevent="saveIdea" class="project-form">
        <div class="form-group">
          <label for="idea-title">Название</label>
          <input id="idea-title" v-model="form.title" class="input" maxlength="200" required autofocus :disabled="loading" />
        </div>
        <div class="form-group">
          <label for="idea-description">Зачем она и в чём суть</label>
          <textarea id="idea-description" v-model="form.description" class="input textarea" rows="4" maxlength="10000" :disabled="loading" />
        </div>
        <div class="form-row">
          <div class="form-group">
            <label for="idea-category">Категория</label>
            <select id="idea-category" v-model="form.category" class="input" :disabled="loading">
              <option v-for="item in categories" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </div>
          <div class="form-group">
            <label for="idea-status">Статус</label>
            <select id="idea-status" v-model="form.status" class="input" :disabled="loading || (editingId && form.status === 'IN_WORK')">
              <option v-for="item in statuses" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </div>
        </div>
        <div class="form-actions">
          <button type="submit" class="btn btn-primary" :disabled="loading">{{ editingId ? 'Сохранить' : 'Сохранить идею' }}</button>
          <button type="button" class="btn btn-ghost" :disabled="loading" @click="cancelForm">Отмена</button>
        </div>
      </form>
    </section>

    <section v-if="promoteIdea" class="card" style="margin-bottom: 1.5rem">
      <h2>Взять идею в работу</h2>
      <p>«{{ promoteIdea.title }}» станет новым Проектом. Выберите его Область жизни.</p>
      <div class="form-group">
        <label for="promote-area">Область жизни Проекта</label>
        <select id="promote-area" v-model="promoteLifeAreaId" class="input" :disabled="loading">
          <option disabled value="">Выберите…</option>
          <option v-for="area in areas" :key="area.id" :value="String(area.id)">{{ area.name }}</option>
        </select>
      </div>
      <div class="form-actions">
        <button class="btn btn-primary" :disabled="loading" @click="promote">Создать Проект</button>
        <button class="btn btn-ghost" :disabled="loading" @click="cancelPromote">Отмена</button>
      </div>
    </section>

    <section class="card">
      <div class="projects-toolbar">
        <h2 style="margin: 0">Идеи</h2>
        <div class="projects-toolbar-actions">
          <select v-model="categoryFilter" class="input filter-select" :disabled="loading" aria-label="Фильтр по категории" @change="loadAll">
            <option value="">Все категории</option>
            <option v-for="item in categories" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
          <select v-model="statusFilter" class="input filter-select" :disabled="loading" aria-label="Фильтр по статусу" @change="loadAll">
            <option value="">Все статусы</option>
            <option v-for="item in statuses" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
          <button v-if="!showForm" class="btn btn-primary" :disabled="loading" @click="openCreate">+ Добавить</button>
        </div>
      </div>

      <div v-if="loading && !visibleIdeas.length" class="loading">Загрузка…</div>
      <div v-else-if="!visibleIdeas.length" class="empty-state">
        <h3>Пока пусто</h3>
        <p>Сохраните первую идею, чтобы не держать её в голове.</p>
        <button class="btn btn-primary" @click="openCreate">Добавить идею</button>
      </div>
      <div v-else class="project-tree">
        <div v-for="idea in visibleIdeas" :key="idea.id" class="project-row">
          <div class="project-row-main">
            <span class="project-title">{{ idea.title }}</span>
            <span class="project-meta">
              <span>{{ categoryLabel(idea.category) }}</span>
              <span> · {{ statusLabel(idea.status) }}</span>
              <span v-if="idea.description"> · {{ idea.description }}</span>
            </span>
            <div class="idea-synergy" aria-label="Синергия идеи">
              <span v-for="item in ideaSynergies(idea.id)" :key="item.id" class="idea-synergy-chip">
                {{ impactLabel(item.impact) }} {{ item.sphereName }}
                <button type="button" title="Удалить Синергию" :disabled="loading" @click="removeSynergy(item)">×</button>
              </span>
              <span v-if="availableSpheres(idea).length" class="idea-synergy-add">
                <select v-model="synergyFormFor(idea.id).sphereId" class="input" :disabled="loading" :aria-label="`Сфера жизни для ${idea.title}`">
                  <option value="">+ Синергия</option>
                  <option v-for="sphere in availableSpheres(idea)" :key="sphere.id" :value="String(sphere.id)">{{ sphere.name }}</option>
                </select>
                <select v-model="synergyFormFor(idea.id).impact" class="input" :disabled="loading" :aria-label="`Влияние Синергии для ${idea.title}`">
                  <option value="POSITIVE">+</option>
                  <option value="NEGATIVE">−</option>
                  <option value="NEUTRAL">0</option>
                </select>
                <button type="button" class="btn btn-ghost" :disabled="loading || !synergyFormFor(idea.id).sphereId" @click="addSynergy(idea)">Добавить</button>
              </span>
            </div>
          </div>
          <div class="life-area-actions">
            <button v-if="idea.status === 'BANK'" class="btn btn-ghost" :disabled="loading" @click="openPromote(idea)">Взять в работу</button>
            <button class="icon-btn" title="Изменить" aria-label="Изменить" :disabled="loading" @click="openEdit(idea)">✎</button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
