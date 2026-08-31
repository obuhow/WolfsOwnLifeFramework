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
import { useRouter, useRoute } from 'vue-router'
import { apiBase } from '../api'
import ConfirmInline from './ConfirmInline.vue'

const router = useRouter()
const route = useRoute()

const areas = ref([])
const projects = ref([])
const loading = ref(false)
const error = ref('')
const success = ref('')
const showForm = ref(false)
const editingId = ref(null)
const filterAreaId = ref('')

const form = ref({
  lifeAreaId: '',
  parentId: '',
  title: '',
  description: '',
  startDate: '',
  endDate: '',
  totalPlanHours: '',
  planDistribution: 'NONE'
})

const filteredProjects = computed(() => {
  if (!filterAreaId.value) return projects.value
  const id = Number(filterAreaId.value)
  return projects.value.filter(p => p.lifeAreaId === id)
})

const treeRows = computed(() => buildTreeRows(filteredProjects.value))

const parentOptions = computed(() => {
  const areaId = form.value.lifeAreaId ? Number(form.value.lifeAreaId) : null
  return projects.value
    .filter(p => {
      if (editingId.value && p.id === editingId.value) return false
      if (areaId && p.lifeAreaId !== areaId) return false
      // exclude descendants of the edited project to reduce cycle risk in UI
      if (editingId.value && isDescendantOf(p.id, editingId.value)) return false
      return true
    })
    .map(p => ({
      id: p.id,
      label: `${indentLabel(p)}`
    }))
})

function areaName(id) {
  return areas.value.find(a => a.id === id)?.name || '—'
}

function areaColor(id) {
  return areas.value.find(a => a.id === id)?.color || '#3d5a4a'
}

function buildTreeRows(list) {
  const byParent = new Map()
  for (const p of list) {
    const key = p.parentId == null ? 'root' : String(p.parentId)
    if (!byParent.has(key)) byParent.set(key, [])
    byParent.get(key).push(p)
  }
  for (const kids of byParent.values()) {
    kids.sort((a, b) => a.title.localeCompare(b.title, 'ru'))
  }

  const rows = []
  const walk = (parentKey, depth) => {
    const kids = byParent.get(parentKey) || []
    for (const p of kids) {
      rows.push({ ...p, depth })
      walk(String(p.id), depth + 1)
    }
  }
  walk('root', 0)

  // orphans (parent filtered out) — show at root with mark
  const shown = new Set(rows.map(r => r.id))
  for (const p of list) {
    if (!shown.has(p.id)) {
      rows.push({ ...p, depth: 0, orphan: true })
    }
  }
  return rows
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

function indentLabel(p) {
  // flat label for select: include area + path depth approx by parent chain
  let depth = 0
  let cursor = p
  const guard = new Set()
  while (cursor && cursor.parentId != null && !guard.has(cursor.parentId)) {
    guard.add(cursor.parentId)
    depth += 1
    cursor = projects.value.find(x => x.id === cursor.parentId)
  }
  return `${'— '.repeat(depth)}${p.title}`
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

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    await loadAreas()
    await loadProjects()
    applyEditQuery()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

// Внешняя точка входа: /projects?edit={id} открывает форму редактирования
// проекта (используется из «Утреннего обхода»). Форма не меняется — лишь
// переиспользуется существующий openEdit.
function applyEditQuery() {
  const rawId = route.query.edit
  if (rawId == null) return
  const id = Number(Array.isArray(rawId) ? rawId[0] : rawId)
  if (!Number.isFinite(id)) return
  const project = projects.value.find(p => p.id === id)
  if (project) openEdit(project)
}

function openCreate(parent = null) {
  editingId.value = null
  const defaultArea = parent
    ? parent.lifeAreaId
    : (filterAreaId.value ? Number(filterAreaId.value) : (areas.value[0]?.id || ''))
  form.value = {
    lifeAreaId: defaultArea ? String(defaultArea) : '',
    parentId: parent ? String(parent.id) : '',
    title: '',
    description: '',
    startDate: '',
    endDate: '',
    totalPlanHours: '',
    planDistribution: 'NONE'
  }
  showForm.value = true
}

function openEdit(project) {
  editingId.value = project.id
  form.value = {
    lifeAreaId: String(project.lifeAreaId),
    parentId: project.parentId != null ? String(project.parentId) : '',
    title: project.title || '',
    description: project.description || '',
    startDate: project.startDate || '',
    endDate: project.endDate || '',
    totalPlanHours: project.totalPlanHours != null ? String(project.totalPlanHours) : '',
    planDistribution: project.planDistribution || 'NONE'
  }
  showForm.value = true
}

function cancelForm() {
  showForm.value = false
  editingId.value = null
}

function payloadFromForm() {
  const hoursRaw = String(form.value.totalPlanHours || '').trim()
  return {
    lifeAreaId: Number(form.value.lifeAreaId),
    parentId: form.value.parentId ? Number(form.value.parentId) : null,
    title: form.value.title.trim(),
    description: form.value.description.trim() || null,
    startDate: form.value.startDate || null,
    endDate: form.value.endDate || null,
    totalPlanHours: hoursRaw === '' ? null : Number(hoursRaw),
    planDistribution: form.value.planDistribution
  }
}

async function saveProject() {
  if (!form.value.title.trim()) {
    error.value = 'Название обязательно'
    return
  }
  if (!form.value.lifeAreaId) {
    error.value = 'Выберите область жизни'
    return
  }

  loading.value = true
  error.value = ''
  success.value = ''
  try {
    const headers = authHeaders(true)
    if (!headers) return
    const isEdit = editingId.value != null
    const url = isEdit
      ? `${apiBase()}/projects/${editingId.value}`
      : `${apiBase()}/projects`
    const res = await fetch(url, {
      method: isEdit ? 'PUT' : 'POST',
      headers,
      body: JSON.stringify(payloadFromForm())
    })
    if (!res.ok) {
      const data = await res.json().catch(() => ({}))
      throw new Error(data.message || `HTTP ${res.status}`)
    }
    const savedProject = await res.json()
    if (form.value.planDistribution !== 'NONE' && savedProject.id) {
      const distributionRes = await fetch(`${apiBase()}/projects/${savedProject.id}/plan-distribution`, {
        method: 'POST',
        headers,
        body: JSON.stringify({ mode: form.value.planDistribution })
      })
      if (!distributionRes.ok) {
        const data = await distributionRes.json().catch(() => ({}))
        throw new Error(data.message || `Распределение: HTTP ${distributionRes.status}`)
      }
    }
    success.value = isEdit ? 'Проект обновлён' : 'Проект создан'
    cancelForm()
    await loadProjects()
    setTimeout(() => { success.value = '' }, 3000)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function deleteProject(project) {
  loading.value = true
  error.value = ''
  success.value = ''
  try {
    const headers = authHeaders()
    if (!headers) return
    const res = await fetch(`${apiBase()}/projects/${project.id}`, {
      method: 'DELETE',
      headers
    })
    if (!res.ok) {
      const data = await res.json().catch(() => ({}))
      throw new Error(data.message || `HTTP ${res.status}`)
    }
    success.value = 'Проект удалён'
    await loadProjects()
    setTimeout(() => { success.value = '' }, 3000)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

function openProject(project) {
  router.push(`/projects/${project.id}`)
}

onMounted(loadAll)
</script>

<template>
  <div class="projects-page">
    <header class="page-header">
      <h1>Проекты</h1>
      <p class="eyebrow">Инициативы внутри областей жизни — с подпроектами, сроками и плановыми часами</p>
    </header>

    <div v-if="error" class="alert alert-error" style="margin-bottom: 1.5rem">{{ error }}</div>
    <div v-if="success" class="alert alert-success" style="margin-bottom: 1.5rem">{{ success }}</div>

    <section v-if="showForm" class="card" style="margin-bottom: 1.5rem">
      <h2>{{ editingId ? 'Редактировать проект' : 'Новый проект' }}</h2>
      <form @submit.prevent="saveProject" class="project-form">
        <div class="form-row">
          <div class="form-group">
            <label for="project-area">Область жизни</label>
            <select id="project-area" v-model="form.lifeAreaId" class="input" :disabled="loading" required>
              <option disabled value="">Выберите…</option>
              <option v-for="a in areas" :key="a.id" :value="String(a.id)">{{ a.name }}</option>
            </select>
          </div>
          <div class="form-group">
            <label for="project-parent">Родитель (подпроект)</label>
            <select id="project-parent" v-model="form.parentId" class="input" :disabled="loading">
              <option value="">— без родителя —</option>
              <option v-for="p in parentOptions" :key="p.id" :value="String(p.id)">{{ p.label }}</option>
            </select>
          </div>
        </div>

        <div class="form-group">
          <label for="project-title">Название</label>
          <input
            id="project-title"
            v-model="form.title"
            type="text"
            class="input"
            maxlength="200"
            required
            :disabled="loading"
            placeholder="Например: WOLF 0.1, Подготовка к ISTQB…"
            autofocus
          />
        </div>

        <div class="form-group">
          <label for="project-desc">Описание</label>
          <textarea
            id="project-desc"
            v-model="form.description"
            class="input textarea"
            rows="3"
            maxlength="10000"
            :disabled="loading"
            placeholder="Зачем этот проект, границы, заметки…"
          />
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="project-start">Начало</label>
            <input id="project-start" v-model="form.startDate" type="date" class="input" :disabled="loading" />
          </div>
          <div class="form-group">
            <label for="project-end">Финальный срок</label>
            <input id="project-end" v-model="form.endDate" type="date" class="input" :disabled="loading" />
          </div>
          <div class="form-group">
            <label for="project-hours">План, часы</label>
            <input
              id="project-hours"
              v-model="form.totalPlanHours"
              type="text"
              inputmode="decimal"
              class="input"
              :disabled="loading"
              placeholder="опционально"
            />
          </div>
        </div>

        <fieldset class="distribution-options">
          <legend>Распределение времени</legend>
          <label><input v-model="form.planDistribution" value="EVEN_ALL_DAYS" type="radio" :disabled="!form.startDate || !form.endDate || !form.totalPlanHours" /> Равномерно по дням</label>
          <label><input v-model="form.planDistribution" value="EVEN_WEEKDAYS" type="radio" :disabled="!form.startDate || !form.endDate || !form.totalPlanHours" /> Равномерно по будням</label>
          <small v-if="!form.startDate || !form.endDate || !form.totalPlanHours" class="muted">Нужны даты и плановые часы</small>
        </fieldset>

        <div class="form-actions">
          <button type="submit" class="btn btn-primary" :disabled="loading">
            <span v-if="loading">Сохранение…</span>
            <span v-else>{{ editingId ? 'Сохранить' : 'Создать' }}</span>
          </button>
          <button type="button" class="btn btn-ghost" :disabled="loading" @click="cancelForm">Отмена</button>
        </div>
      </form>
    </section>

    <section class="card">
      <div class="projects-toolbar">
        <h2 style="margin: 0">Дерево проектов</h2>
        <div class="projects-toolbar-actions">
          <select v-model="filterAreaId" class="input filter-select" :disabled="loading" aria-label="Фильтр по области">
            <option value="">Все области</option>
            <option v-for="a in areas" :key="a.id" :value="String(a.id)">{{ a.name }}</option>
          </select>
          <button v-if="!showForm" class="btn btn-primary" :disabled="loading || areas.length === 0" @click="openCreate()">
            + Добавить
          </button>
        </div>
      </div>

      <div v-if="areas.length === 0 && !loading" class="empty-state">
        <h3>Сначала области</h3>
        <p>Проект живёт внутри Области жизни. Создайте хотя бы одну область, затем вернитесь сюда.</p>
        <router-link to="/life-areas" class="btn btn-primary">К областям жизни</router-link>
      </div>

      <div v-else-if="loading && projects.length === 0" class="loading">Загрузка…</div>

      <div v-else-if="treeRows.length === 0" class="empty-state">
        <h3>Пока пусто</h3>
        <p>Создайте первый проект — цель или инициативу с опциональными сроками и планом часов.</p>
        <button class="btn btn-primary" :disabled="loading" @click="openCreate()">Создать проект</button>
      </div>

      <div v-else class="project-tree">
        <div
          v-for="row in treeRows"
          :key="row.id"
          class="project-row"
          :style="{ paddingLeft: `${1 + row.depth * 1.25}rem` }"
        >
          <span class="life-area-color" :style="{ background: areaColor(row.lifeAreaId) }" :title="areaName(row.lifeAreaId)" />
          <div class="project-row-main" @click="openProject(row)">
            <span class="project-title">{{ row.title }}</span>
            <span class="project-meta">
              <span>{{ areaName(row.lifeAreaId) }}</span>
              <span v-if="row.startDate || row.endDate">
                ·
                {{ row.startDate || '…' }} — {{ row.endDate || '…' }}
              </span>
              <span v-if="row.totalPlanHours != null"> · {{ row.totalPlanHours }} ч</span>
              <span v-if="row.orphan" class="orphan-mark"> · родитель скрыт фильтром</span>
            </span>
          </div>
          <div class="life-area-actions">
            <button class="icon-btn" title="Открыть" aria-label="Открыть" :disabled="loading" @click="openProject(row)">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"></path>
                <polyline points="15 3 21 3 21 9"></polyline>
                <line x1="10" y1="14" x2="21" y2="3"></line>
              </svg>
            </button>
            <button class="icon-btn" title="Подпроект" aria-label="Добавить подпроект" :disabled="loading" @click="openCreate(row)">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <line x1="12" y1="5" x2="12" y2="19"></line>
                <line x1="5" y1="12" x2="19" y2="12"></line>
              </svg>
            </button>
            <button class="icon-btn" title="Изменить" aria-label="Изменить" :disabled="loading" @click="openEdit(row)">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
              </svg>
            </button>
            <ConfirmInline
              label="Удалить"
              :question="`Удалить проект «${row.title}» и все подпроекты?`"
              confirm-label="Да, удалить"
              :disabled="loading"
              @confirm="deleteProject(row)"
            />
          </div>
        </div>
      </div>
    </section>
  </div>
</template>