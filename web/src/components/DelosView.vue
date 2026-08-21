<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiBase } from '../api'
import ConfirmInline from './ConfirmInline.vue'

const router = useRouter()

const delos = ref([])
const projects = ref([])
const loading = ref(false)
const error = ref('')
const success = ref('')
const showForm = ref(false)
const editingId = ref(null)
const importFile = ref(null)
const importOpen = ref(false)
const addImportedToWeek = ref(true)
const skipOverlapCheck = ref(false)
const importing = ref(false)

const form = ref({
  title: '',
  description: '',
  executionMode: 'SELF',
  projectIds: [],
  primaryProjectId: ''
})

const executionModes = [
  { value: 'SELF', label: 'Сам' },
  { value: 'DELEGATABLE', label: 'Можно делегировать' },
  { value: 'AUTOMATABLE', label: 'Можно автоматизировать' }
]

const projectOptions = computed(() =>
  projects.value
    .slice()
    .sort((a, b) => a.title.localeCompare(b.title, 'ru'))
    .map(p => ({ id: p.id, label: p.title }))
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

function modeLabel(mode) {
  return executionModes.find(m => m.value === mode)?.label || mode
}

function projectTitle(id) {
  return projects.value.find(p => p.id === id)?.title || `#${id}`
}

async function loadProjects() {
  const headers = authHeaders()
  if (!headers) return
  const res = await fetch(`${apiBase()}/projects`, { headers })
  if (!res.ok) throw new Error(`Проекты: HTTP ${res.status}`)
  projects.value = await res.json()
}

async function loadDelos() {
  const headers = authHeaders()
  if (!headers) return
  const res = await fetch(`${apiBase()}/delos`, { headers })
  if (!res.ok) throw new Error(`Дела: HTTP ${res.status}`)
  delos.value = await res.json()
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    await Promise.all([loadProjects(), loadDelos()])
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.value = {
    title: '',
    description: '',
    executionMode: 'SELF',
    projectIds: [],
    primaryProjectId: ''
  }
  showForm.value = true
}

function openEdit(delo) {
  editingId.value = delo.id
  form.value = {
    title: delo.title || '',
    description: delo.description || '',
    executionMode: delo.executionMode || 'SELF',
    projectIds: (delo.projectIds || []).map(String),
    primaryProjectId: delo.primaryProjectId != null ? String(delo.primaryProjectId) : ''
  }
  showForm.value = true
}

function cancelForm() {
  showForm.value = false
  editingId.value = null
}

function onProjectsChange() {
  // drop primary if no longer selected
  if (form.value.primaryProjectId && !form.value.projectIds.includes(form.value.primaryProjectId)) {
    form.value.primaryProjectId = form.value.projectIds[0] || ''
  }
  // auto-pick primary when first project selected
  if (!form.value.primaryProjectId && form.value.projectIds.length) {
    form.value.primaryProjectId = form.value.projectIds[0]
  }
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

async function saveDelo() {
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
    const isEdit = editingId.value != null
    const url = isEdit ? `${apiBase()}/delos/${editingId.value}` : `${apiBase()}/delos`
    const res = await fetch(url, {
      method: isEdit ? 'PUT' : 'POST',
      headers,
      body: JSON.stringify(payloadFromForm())
    })
    if (!res.ok) {
      const data = await res.json().catch(() => ({}))
      throw new Error(data.message || `HTTP ${res.status}`)
    }
    success.value = isEdit ? 'Дело обновлено' : 'Дело создано'
    cancelForm()
    await loadDelos()
    setTimeout(() => { success.value = '' }, 3000)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function deleteDelo(delo) {
  loading.value = true
  error.value = ''
  success.value = ''
  try {
    const headers = authHeaders()
    if (!headers) return
    const res = await fetch(`${apiBase()}/delos/${delo.id}`, {
      method: 'DELETE',
      headers
    })
    if (!res.ok) {
      const data = await res.json().catch(() => ({}))
      throw new Error(data.message || `HTTP ${res.status}`)
    }
    success.value = 'Дело удалено'
    await loadDelos()
    setTimeout(() => { success.value = '' }, 3000)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function importCsv() {
  if (!importFile.value) { error.value = 'Выберите CSV-файл'; return }
  importing.value = true
  error.value = ''
  try {
    const headers = authHeaders()
    if (!headers) return
    const data = new FormData()
    data.append('file', importFile.value)
    data.append('addToCurrentWeek', String(addImportedToWeek.value))
    data.append('skipOverlapCheck', String(skipOverlapCheck.value))
    let res
    try {
      res = await fetch(`${apiBase()}/delos/import`, { method: 'POST', headers, body: data })
    } catch (e) {
      throw new Error(`Не удалось связаться с API импорта: ${e instanceof Error ? e.message : String(e)}`)
    }
    const responseText = await res.text()
    let responseBody = {}
    try { responseBody = responseText ? JSON.parse(responseText) : {} } catch { /* plain-text response */ }
    if (!res.ok) {
      throw new Error(responseBody.message || responseText || `Импорт: HTTP ${res.status}`)
    }
    success.value = `Импортировано Дел: ${responseBody.imported}`
    importFile.value = null
    await loadDelos()
  } catch (e) { error.value = e instanceof Error ? e.message : String(e) }
  finally { importing.value = false }
}

function openDelo(delo) {
  router.push(`/delos/${delo.id}`)
}

onMounted(loadAll)
</script>

<template>
  <div class="projects-page">
    <header class="page-header">
      <h1>Дела</h1>
      <p class="eyebrow">Переиспользуемые типы активности: способ исполнения, связи с проектами и основной проект</p>
    </header>

    <div v-if="error" class="alert alert-error" style="margin-bottom: 1.5rem">{{ error }}</div>
    <div v-if="success" class="alert alert-success" style="margin-bottom: 1.5rem">{{ success }}</div>

    <section v-if="showForm" class="card" style="margin-bottom: 1.5rem">
      <h2>{{ editingId ? 'Редактировать Дело' : 'Новое Дело' }}</h2>
      <form class="project-form" @submit.prevent="saveDelo">
        <div class="form-group">
          <label for="delo-title">Название</label>
          <input
            id="delo-title"
            v-model="form.title"
            type="text"
            class="input"
            maxlength="200"
            required
            :disabled="loading"
            placeholder="Например: Код-ревью, Утренний бег…"
            autofocus
          />
        </div>

        <div class="form-group">
          <label for="delo-desc">Описание</label>
          <textarea
            id="delo-desc"
            v-model="form.description"
            class="input textarea"
            rows="3"
            maxlength="10000"
            :disabled="loading"
            placeholder="Что это за занятие само по себе…"
          />
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="delo-mode">Способ исполнения</label>
            <select id="delo-mode" v-model="form.executionMode" class="input" :disabled="loading">
              <option v-for="m in executionModes" :key="m.value" :value="m.value">{{ m.label }}</option>
            </select>
          </div>
          <div class="form-group">
            <label for="delo-projects">Проекты (0..N)</label>
            <select
              id="delo-projects"
              v-model="form.projectIds"
              class="input"
              multiple
              :disabled="loading"
              size="4"
              @change="onProjectsChange"
            >
              <option v-for="p in projectOptions" :key="p.id" :value="String(p.id)">{{ p.label }}</option>
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
              <option v-for="pid in form.projectIds" :key="pid" :value="pid">{{ projectTitle(Number(pid)) }}</option>
            </select>
          </div>
        </div>

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
        <h2 style="margin: 0">Каталог Дел</h2>
        <div class="projects-toolbar-actions">
          <button
            class="btn btn-ghost"
            type="button"
            :aria-expanded="importOpen"
            aria-controls="delo-csv-import"
            @click="importOpen = !importOpen"
          >{{ importOpen ? 'Скрыть импорт CSV' : 'Импорт CSV' }}</button>
          <button v-if="!showForm" class="btn btn-primary" :disabled="loading" @click="openCreate">
            + Добавить
          </button>
        </div>
      </div>

      <section v-if="importOpen" id="delo-csv-import" class="csv-import-panel" aria-label="Импорт Дел из CSV">
        <div class="csv-import-row">
          <input id="delo-csv-file" type="file" accept=".csv,text/csv" :disabled="importing" @change="importFile = $event.target.files[0] || null" />
          <button class="btn btn-primary" :disabled="importing || !importFile" @click="importCsv">
            {{ importing ? 'Импорт…' : 'Загрузить' }}
          </button>
        </div>
        <label><input v-model="addImportedToWeek" type="checkbox" :disabled="importing" /> Добавить в Бэклог текущей недели</label>
        <label><input v-model="skipOverlapCheck" type="checkbox" :disabled="importing" /> Пропустить проверку пересечений</label>
        <div class="csv-schema">
          <p class="csv-schema-title">Схема (запятая-разделитель):</p>
          <code>title,date,startAt,endAt,description,executionMode,projects,lifeArea</code>
          <ul class="csv-schema-list">
            <li><strong>title</strong> — название Дела (обязательно)</li>
            <li><strong>date</strong> — <code>YYYY-MM-DD</code></li>
            <li><strong>startAt</strong> / <strong>endAt</strong> — <code>HH:mm</code> по 15-минутной сетке</li>
            <li><strong>executionMode</strong> — <code>SELF</code> / <code>DELEGATABLE</code> / <code>AUTOMATABLE</code></li>
            <li><strong>projects</strong> — названия Проектов через <code>|</code></li>
            <li><strong>lifeArea</strong> — существующая Область жизни; обязательна, если Проект новый</li>
          </ul>
          <p class="csv-schema-note">
            Поля с запятыми заключайте в кавычки. Отсутствующие Проекты создаются только при явной существующей Области жизни;
            Области жизни никогда не создаются автоматически. Файл проверяется целиком: при ошибке ничего не импортируется.
          </p>
        </div>
      </section>

      <div v-if="loading && delos.length === 0" class="loading">Загрузка…</div>

      <div v-else-if="delos.length === 0" class="empty-state">
        <h3>Пока пусто</h3>
        <p>Создайте первое Дело — шаблон активности без привязки к моменту времени. Проект не обязателен.</p>
        <button class="btn btn-primary" :disabled="loading" @click="openCreate">Создать Дело</button>
      </div>

      <div v-else class="project-tree">
        <div v-for="d in delos" :key="d.id" class="project-row">
          <div class="project-row-main" @click="openDelo(d)">
            <span class="project-title">{{ d.title }}</span>
            <span class="project-meta">
              <span>{{ modeLabel(d.executionMode) }}</span>
              <span v-if="d.projectIds && d.projectIds.length">
                · {{ d.projectIds.length }} проект{{ d.projectIds.length === 1 ? '' : (d.projectIds.length < 5 ? 'а' : 'ов') }}
              </span>
              <span v-else> · без проекта</span>
              <span v-if="d.primaryProjectId"> · основной: {{ projectTitle(d.primaryProjectId) }}</span>
            </span>
          </div>
          <div class="life-area-actions">
            <button class="icon-btn" title="Открыть" aria-label="Открыть" :disabled="loading" @click="openDelo(d)">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"></path>
                <polyline points="15 3 21 3 21 9"></polyline>
                <line x1="10" y1="14" x2="21" y2="3"></line>
              </svg>
            </button>
            <button class="icon-btn" title="Изменить" aria-label="Изменить" :disabled="loading" @click="openEdit(d)">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
              </svg>
            </button>
            <ConfirmInline
              label="Удалить"
              :question="`Удалить Дело «${d.title}»?`"
              confirm-label="Да, удалить"
              :disabled="loading"
              @confirm="deleteDelo(d)"
            />
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
