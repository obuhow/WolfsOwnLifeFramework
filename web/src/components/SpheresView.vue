<script setup>
/**
 * Управление потоком → Диаграмма компетенций (было «Сферы жизни», ticket 09;
 * переименование + поглощение Синергии — release 0.5 ticket 01).
 * Ruled reference list: name + concise description. Spheres are reference data,
 * never a score or judgement of the user. The «Синергия» tab below is the
 * former standalone SynergyView.vue, consolidated as a section of this page.
 */
import { computed, onMounted, ref } from 'vue'
import { apiBase, authHeaders } from '../api'
import ConfirmInline from './ConfirmInline.vue'

const tab = ref('spheres') // 'spheres' | 'synergy'

// --- Диаграмма компетенций (справочник сфер) --------------------------------
const spheres = ref([])
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const showArchived = ref(false)

const form = ref({ name: '', description: '' })
const showForm = ref(false)
const editingId = ref(null)

const visible = computed(() =>
  spheres.value.filter(s => showArchived.value || !s.archived)
)

async function load() {
  const headers = authHeaders()
  if (!headers) return
  loading.value = true
  error.value = ''
  try {
    const res = await fetch(`${apiBase()}/life-spheres`, { headers })
    if (!res.ok) throw new Error(`Диаграмма компетенций: HTTP ${res.status}`)
    spheres.value = await res.json()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

function startCreate() {
  editingId.value = null
  form.value = { name: '', description: '' }
  showForm.value = true
}

function startEdit(sphere) {
  editingId.value = sphere.id
  form.value = { name: sphere.name, description: sphere.description || '' }
  showForm.value = true
}

function cancelForm() {
  showForm.value = false
  editingId.value = null
}

async function save() {
  const headers = authHeaders(true)
  if (!headers || !form.value.name.trim()) return
  busy.value = true
  error.value = ''
  try {
    const url = editingId.value ? `${apiBase()}/life-spheres/${editingId.value}` : `${apiBase()}/life-spheres`
    const res = await fetch(url, {
      method: editingId.value ? 'PUT' : 'POST',
      headers,
      body: JSON.stringify({ name: form.value.name.trim(), description: form.value.description.trim() || null })
    })
    if (!res.ok) {
      const text = await res.text()
      let message = `Сохранение: HTTP ${res.status}`
      try { message = JSON.parse(text).message || message } catch { if (text) message = text }
      throw new Error(message)
    }
    cancelForm()
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

async function toggleArchive(sphere) {
  const headers = authHeaders(true)
  if (!headers) return
  busy.value = true
  try {
    const res = await fetch(`${apiBase()}/life-spheres/${sphere.id}/archive`, {
      method: 'PUT',
      headers,
      body: JSON.stringify({ archived: !sphere.archived })
    })
    if (!res.ok) throw new Error(`Архив: HTTP ${res.status}`)
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

async function remove(sphere) {
  const headers = authHeaders()
  if (!headers) return
  busy.value = true
  try {
    const res = await fetch(`${apiBase()}/life-spheres/${sphere.id}`, { method: 'DELETE', headers })
    if (!res.ok && res.status !== 204) throw new Error(`Удаление: HTTP ${res.status}`)
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

// --- Синергия (поглощена из SynergyView.vue) --------------------------------
const synergies = ref([])
const synProjects = ref([])
const synIdeas = ref([])

const synLoading = ref(false)
const synBusy = ref(false)
const synError = ref('')

const filterSphere = ref('')
const filterImpact = ref('')

const showSynForm = ref(false)
const synForm = ref({ subjectType: 'project', subjectId: '', sphereId: '', impact: 'POSITIVE' })

const IMPACT_SIGN = { POSITIVE: '+', NEUTRAL: '0', NEGATIVE: '−' }
const IMPACT_LABEL = { POSITIVE: 'поддерживает', NEUTRAL: 'нейтрально', NEGATIVE: 'требует ресурса' }

function subjectTitle(row) {
  if (row.projectId) return synProjects.value.find(p => p.id === row.projectId)?.title || `Проект #${row.projectId}`
  if (row.ideaId) return synIdeas.value.find(i => i.id === row.ideaId)?.title || `Идея #${row.ideaId}`
  if (row.routineId) return `Рутина #${row.routineId}`
  return '—'
}

function subjectKind(row) {
  if (row.projectId) return 'Проект'
  if (row.ideaId) return 'Идея'
  if (row.routineId) return 'Рутина'
  return '—'
}

const synergyRows = computed(() => synergies.value.filter(row => {
  if (filterSphere.value && String(row.sphereId) !== filterSphere.value) return false
  if (filterImpact.value && row.impact !== filterImpact.value) return false
  return true
}))

async function loadSynergy() {
  const headers = authHeaders()
  if (!headers) return
  synLoading.value = true
  synError.value = ''
  try {
    const [synRes, sphRes, projRes, ideaRes] = await Promise.all([
      fetch(`${apiBase()}/synergies`, { headers }),
      fetch(`${apiBase()}/life-spheres`, { headers }),
      fetch(`${apiBase()}/projects`, { headers }),
      fetch(`${apiBase()}/ideas`, { headers })
    ])
    if (!synRes.ok) throw new Error(`Синергия: HTTP ${synRes.status}`)
    synergies.value = await synRes.json()
    if (sphRes.ok) spheres.value = await sphRes.json()
    synProjects.value = projRes.ok ? await projRes.json() : []
    synIdeas.value = ideaRes.ok ? await ideaRes.json() : []
  } catch (e) {
    synError.value = e instanceof Error ? e.message : String(e)
  } finally {
    synLoading.value = false
  }
}

async function saveSynergy() {
  const headers = authHeaders(true)
  if (!headers || !synForm.value.subjectId || !synForm.value.sphereId) return
  synBusy.value = true
  synError.value = ''
  try {
    const body = {
      sphereId: Number(synForm.value.sphereId),
      impact: synForm.value.impact,
      projectId: synForm.value.subjectType === 'project' ? Number(synForm.value.subjectId) : null,
      ideaId: synForm.value.subjectType === 'idea' ? Number(synForm.value.subjectId) : null
    }
    const res = await fetch(`${apiBase()}/synergies`, { method: 'POST', headers, body: JSON.stringify(body) })
    if (!res.ok) {
      const text = await res.text()
      let message = `Сохранение: HTTP ${res.status}`
      try { message = JSON.parse(text).message || message } catch { if (text) message = text }
      throw new Error(message)
    }
    showSynForm.value = false
    synForm.value = { subjectType: 'project', subjectId: '', sphereId: '', impact: 'POSITIVE' }
    await loadSynergy()
  } catch (e) {
    synError.value = e instanceof Error ? e.message : String(e)
  } finally {
    synBusy.value = false
  }
}

async function updateImpact(row, impact) {
  const headers = authHeaders(true)
  if (!headers) return
  synBusy.value = true
  try {
    const res = await fetch(`${apiBase()}/synergies/${row.id}`, {
      method: 'PUT', headers, body: JSON.stringify({ impact })
    })
    if (!res.ok) throw new Error(`Обновление: HTTP ${res.status}`)
    await loadSynergy()
  } catch (e) {
    synError.value = e instanceof Error ? e.message : String(e)
  } finally {
    synBusy.value = false
  }
}

async function removeSynergy(row) {
  const headers = authHeaders()
  if (!headers) return
  synBusy.value = true
  try {
    const res = await fetch(`${apiBase()}/synergies/${row.id}`, { method: 'DELETE', headers })
    if (!res.ok && res.status !== 204) throw new Error(`Удаление: HTTP ${res.status}`)
    await loadSynergy()
  } catch (e) {
    synError.value = e instanceof Error ? e.message : String(e)
  } finally {
    synBusy.value = false
  }
}

function setTab(next) {
  tab.value = next
  if (next === 'synergy' && !synergies.value.length && !synLoading.value) loadSynergy()
}

onMounted(load)
</script>

<template>
  <div class="spheres-page">
    <header class="page-header spheres-header">
      <div>
        <h1>Диаграмма компетенций</h1>
        <p class="eyebrow">Управление потоком · справочник сфер и их синергия с Проектами/Идеями</p>
      </div>
      <div class="spheres-actions" v-if="tab === 'spheres'">
        <label class="inline-check">
          <input v-model="showArchived" type="checkbox" />
          <span>Показывать архивные</span>
        </label>
        <button v-if="!showForm" type="button" class="btn btn-primary" :disabled="busy" @click="startCreate">Добавить сферу</button>
      </div>
      <button v-else-if="!showSynForm" type="button" class="btn btn-primary" :disabled="synBusy" @click="showSynForm = true">
        Добавить связь
      </button>
    </header>

    <div class="tabs" role="tablist" aria-label="Разделы Диаграммы компетенций">
      <button type="button" class="tab" role="tab" :aria-selected="tab === 'spheres'" :class="{ active: tab === 'spheres' }" @click="setTab('spheres')">Сферы</button>
      <button type="button" class="tab" role="tab" :aria-selected="tab === 'synergy'" :class="{ active: tab === 'synergy' }" @click="setTab('synergy')">Синергия</button>
    </div>

    <section v-if="tab === 'spheres'">
      <p v-if="error" class="alert alert-error">{{ error }}</p>

      <form v-if="showForm" class="sphere-form" @submit.prevent="save">
        <label>
          <span>Название</span>
          <input v-model="form.name" class="input" required maxlength="120" />
        </label>
        <label>
          <span>Краткое описание</span>
          <input v-model="form.description" class="input" maxlength="400" />
        </label>
        <div class="form-actions">
          <button type="button" class="btn btn-ghost" :disabled="busy" @click="cancelForm">Отмена</button>
          <button type="submit" class="btn btn-primary" :disabled="busy || !form.name.trim()">
            {{ editingId ? 'Сохранить' : 'Добавить' }}
          </button>
        </div>
      </form>

      <div v-if="loading" class="loading">Загрузка…</div>

      <ul v-else-if="visible.length" class="sphere-list">
        <li v-for="sphere in visible" :key="sphere.id" class="sphere-row" :class="{ archived: sphere.archived }">
          <div class="sphere-body">
            <span class="sphere-name">{{ sphere.name }}</span>
            <span class="sphere-desc">{{ sphere.description || 'Описание не задано.' }}</span>
          </div>
          <div class="sphere-row-actions">
            <span v-if="sphere.archived" class="sphere-state">в архиве</span>
            <button type="button" class="btn btn-ghost btn-sm" :disabled="busy" @click="startEdit(sphere)">Изменить</button>
            <button type="button" class="btn btn-ghost btn-sm" :disabled="busy" @click="toggleArchive(sphere)">
              {{ sphere.archived ? 'Вернуть' : 'В архив' }}
            </button>
            <ConfirmInline
              label="Удалить"
              :question="`Удалить сферу «${sphere.name}»?`"
              confirm-label="Да, удалить"
              :disabled="busy"
              @confirm="remove(sphere)"
            />
          </div>
        </li>
      </ul>

      <p v-else class="hint">Сферы пока не заданы.</p>
    </section>

    <section v-else class="synergy-section">
      <p class="eyebrow synergy-note">Влияние Проектов и Идей на сферы жизни. Влияние выражается только знаком и словом — никогда цветом или тревожным оформлением.</p>

      <p v-if="synError" class="alert alert-error">{{ synError }}</p>

      <form v-if="showSynForm" class="synergy-form" @submit.prevent="saveSynergy">
        <label>
          <span>Тип субъекта</span>
          <select v-model="synForm.subjectType" class="input">
            <option value="project">Проект</option>
            <option value="idea">Идея</option>
          </select>
        </label>
        <label>
          <span>Субъект</span>
          <select v-model="synForm.subjectId" class="input" required>
            <option value="">—</option>
            <option v-for="item in (synForm.subjectType === 'project' ? synProjects : synIdeas)" :key="item.id" :value="item.id">
              {{ item.title }}
            </option>
          </select>
        </label>
        <label>
          <span>Сфера жизни</span>
          <select v-model="synForm.sphereId" class="input" required>
            <option value="">—</option>
            <option v-for="sphere in spheres" :key="sphere.id" :value="sphere.id">{{ sphere.name }}</option>
          </select>
        </label>
        <label>
          <span>Влияние</span>
          <select v-model="synForm.impact" class="input">
            <option value="POSITIVE">+ поддерживает</option>
            <option value="NEUTRAL">0 нейтрально</option>
            <option value="NEGATIVE">− требует ресурса</option>
          </select>
        </label>
        <div class="form-actions">
          <button type="button" class="btn btn-ghost" :disabled="synBusy" @click="showSynForm = false">Отмена</button>
          <button type="submit" class="btn btn-primary" :disabled="synBusy || !synForm.subjectId || !synForm.sphereId">Добавить</button>
        </div>
      </form>

      <div class="synergy-filters">
        <label>
          <span>Сфера</span>
          <select v-model="filterSphere" class="input">
            <option value="">Все</option>
            <option v-for="sphere in spheres" :key="sphere.id" :value="String(sphere.id)">{{ sphere.name }}</option>
          </select>
        </label>
        <label>
          <span>Влияние</span>
          <select v-model="filterImpact" class="input">
            <option value="">Любое</option>
            <option value="POSITIVE">+ поддерживает</option>
            <option value="NEUTRAL">0 нейтрально</option>
            <option value="NEGATIVE">− требует ресурса</option>
          </select>
        </label>
      </div>

      <div v-if="synLoading" class="loading">Загрузка…</div>

      <ul v-else-if="synergyRows.length" class="synergy-list">
        <li v-for="row in synergyRows" :key="row.id" class="synergy-row">
          <div class="synergy-subject">
            <span class="subject-title">{{ subjectTitle(row) }}</span>
            <span class="subject-kind">{{ subjectKind(row) }}</span>
          </div>
          <span class="synergy-sphere">{{ row.sphereName }}</span>
          <span class="synergy-impact">
            <span class="impact-sign">{{ IMPACT_SIGN[row.impact] }}</span>
            <span class="impact-label">{{ IMPACT_LABEL[row.impact] }}</span>
          </span>
          <div class="synergy-actions">
            <select
              class="input input-sm"
              :value="row.impact"
              :disabled="synBusy"
              aria-label="Изменить влияние"
              @change="updateImpact(row, $event.target.value)"
            >
              <option value="POSITIVE">+ поддерживает</option>
              <option value="NEUTRAL">0 нейтрально</option>
              <option value="NEGATIVE">− требует ресурса</option>
            </select>
            <ConfirmInline
              label="Удалить"
              :question="`Удалить связь «${subjectTitle(row)}» — «${row.sphereName}»?`"
              confirm-label="Да, удалить"
              :disabled="synBusy"
              @confirm="removeSynergy(row)"
            />
          </div>
        </li>
      </ul>

      <p v-else class="hint">Связей синергии пока нет.</p>
    </section>
  </div>
</template>

<style scoped>
.spheres-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

.spheres-actions { display: flex; align-items: center; gap: 1rem; flex-wrap: wrap; }

.tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--wolf-rule);
  margin-bottom: 16px;
}

.tab {
  padding: 8px 4px;
  margin-right: 20px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--wolf-muted);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}

.tab.active {
  color: var(--wolf-ink);
  border-bottom-color: var(--wolf-ink);
  font-weight: 600;
}

.inline-check {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--wolf-muted);
  font-size: 12px;
}

.sphere-form {
  display: grid;
  gap: 12px;
  padding: 14px 0 16px;
  border-top: 1px solid var(--wolf-rule);
  border-bottom: 1px solid var(--wolf-rule);
  margin-bottom: 16px;
}

.sphere-form label { display: grid; gap: 4px; color: var(--wolf-muted); font-size: 12px; }
.form-actions { display: flex; justify-content: flex-end; gap: 1rem; }

.sphere-list { list-style: none; margin: 0; padding: 0; border-top: 1px solid var(--wolf-rule); }

.sphere-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid var(--wolf-rule);
}

.sphere-row.archived .sphere-name { color: var(--wolf-muted); }
.sphere-body { display: grid; gap: 3px; min-width: 0; }
.sphere-name { color: var(--wolf-ink); font-size: 13px; font-weight: 600; }
.sphere-desc { color: var(--wolf-muted); font-size: 12px; line-height: 1.45; }
.sphere-state { color: var(--wolf-muted); font-size: 11px; }
.sphere-row-actions { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; }

.synergy-note { margin: 0 0 14px; }

.synergy-form,
.synergy-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: flex-end;
  padding: 12px 0 14px;
  border-top: 1px solid var(--wolf-rule);
  border-bottom: 1px solid var(--wolf-rule);
  margin-bottom: 14px;
}

.synergy-form label,
.synergy-filters label {
  display: grid;
  gap: 4px;
  color: var(--wolf-muted);
  font-size: 12px;
  min-width: 10rem;
}

.synergy-list { list-style: none; margin: 0; padding: 0; border-top: 1px solid var(--wolf-rule); }

.synergy-row {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr) auto auto;
  gap: 12px;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid var(--wolf-rule);
}

.synergy-subject { display: grid; gap: 2px; min-width: 0; }
.subject-title {
  color: var(--wolf-ink);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.subject-kind { color: var(--wolf-muted); font-size: 11px; }
.synergy-sphere { color: var(--wolf-muted); font-size: 12px; }

/* Sign + word only: impact is never colour-coded, negative is not a warning. */
.synergy-impact { display: inline-flex; align-items: baseline; gap: 0.4rem; }
.impact-sign {
  color: var(--wolf-ink);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  min-width: 1ch;
}
.impact-label { color: var(--wolf-muted); font-size: 12px; }

.synergy-actions { display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap; }
.input-sm { font-size: 12px; }

@media (max-width: 760px) {
  .synergy-row { grid-template-columns: 1fr; }
  .synergy-actions { justify-content: flex-start; }
}

@media (max-width: 640px) {
  .sphere-row { grid-template-columns: 1fr; }
  .sphere-row-actions { justify-content: flex-start; }
}
</style>
