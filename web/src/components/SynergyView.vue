<script setup>
/**
 * Управление потоком → Синергия (ticket 09).
 * Subject (Проект или Идея) × Сфера жизни × signed impact + description.
 * Impact is expressed with a sign and words only — negative influence is never
 * encoded with red or framed as a failure.
 */
import { computed, onMounted, ref } from 'vue'
import { apiBase, authHeaders } from '../api'
import ConfirmInline from './ConfirmInline.vue'

const synergies = ref([])
const spheres = ref([])
const projects = ref([])
const ideas = ref([])

const loading = ref(false)
const busy = ref(false)
const error = ref('')

const filterSphere = ref('')
const filterImpact = ref('')

const showForm = ref(false)
const form = ref({ subjectType: 'project', subjectId: '', sphereId: '', impact: 'POSITIVE' })

const IMPACT_SIGN = { POSITIVE: '+', NEUTRAL: '0', NEGATIVE: '−' }
const IMPACT_LABEL = { POSITIVE: 'поддерживает', NEUTRAL: 'нейтрально', NEGATIVE: 'требует ресурса' }

function subjectTitle(row) {
  if (row.projectId) return projects.value.find(p => p.id === row.projectId)?.title || `Проект #${row.projectId}`
  if (row.ideaId) return ideas.value.find(i => i.id === row.ideaId)?.title || `Идея #${row.ideaId}`
  if (row.routineId) return `Рутина #${row.routineId}`
  return '—'
}

function subjectKind(row) {
  if (row.projectId) return 'Проект'
  if (row.ideaId) return 'Идея'
  if (row.routineId) return 'Рутина'
  return '—'
}

const rows = computed(() => synergies.value.filter(row => {
  if (filterSphere.value && String(row.sphereId) !== filterSphere.value) return false
  if (filterImpact.value && row.impact !== filterImpact.value) return false
  return true
}))

async function load() {
  const headers = authHeaders()
  if (!headers) return
  loading.value = true
  error.value = ''
  try {
    const [synRes, sphRes, projRes, ideaRes] = await Promise.all([
      fetch(`${apiBase()}/synergies`, { headers }),
      fetch(`${apiBase()}/life-spheres`, { headers }),
      fetch(`${apiBase()}/projects`, { headers }),
      fetch(`${apiBase()}/ideas`, { headers })
    ])
    if (!synRes.ok) throw new Error(`Синергия: HTTP ${synRes.status}`)
    synergies.value = await synRes.json()
    spheres.value = sphRes.ok ? await sphRes.json() : []
    projects.value = projRes.ok ? await projRes.json() : []
    ideas.value = ideaRes.ok ? await ideaRes.json() : []
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function save() {
  const headers = authHeaders(true)
  if (!headers || !form.value.subjectId || !form.value.sphereId) return
  busy.value = true
  error.value = ''
  try {
    const body = {
      sphereId: Number(form.value.sphereId),
      impact: form.value.impact,
      projectId: form.value.subjectType === 'project' ? Number(form.value.subjectId) : null,
      ideaId: form.value.subjectType === 'idea' ? Number(form.value.subjectId) : null
    }
    const res = await fetch(`${apiBase()}/synergies`, { method: 'POST', headers, body: JSON.stringify(body) })
    if (!res.ok) {
      const text = await res.text()
      let message = `Сохранение: HTTP ${res.status}`
      try { message = JSON.parse(text).message || message } catch { if (text) message = text }
      throw new Error(message)
    }
    showForm.value = false
    form.value = { subjectType: 'project', subjectId: '', sphereId: '', impact: 'POSITIVE' }
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

async function updateImpact(row, impact) {
  const headers = authHeaders(true)
  if (!headers) return
  busy.value = true
  try {
    const res = await fetch(`${apiBase()}/synergies/${row.id}`, {
      method: 'PUT', headers, body: JSON.stringify({ impact })
    })
    if (!res.ok) throw new Error(`Обновление: HTTP ${res.status}`)
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

async function remove(row) {
  const headers = authHeaders()
  if (!headers) return
  busy.value = true
  try {
    const res = await fetch(`${apiBase()}/synergies/${row.id}`, { method: 'DELETE', headers })
    if (!res.ok && res.status !== 204) throw new Error(`Удаление: HTTP ${res.status}`)
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="synergy-page">
    <header class="page-header synergy-header">
      <div>
        <h1>Синергия</h1>
        <p class="eyebrow">Управление потоком · влияние Проектов и Идей на Сферы жизни</p>
      </div>
      <button v-if="!showForm" type="button" class="btn btn-primary" :disabled="busy" @click="showForm = true">
        Добавить связь
      </button>
    </header>

    <p v-if="error" class="alert alert-error">{{ error }}</p>

    <form v-if="showForm" class="synergy-form" @submit.prevent="save">
      <label>
        <span>Тип субъекта</span>
        <select v-model="form.subjectType" class="input">
          <option value="project">Проект</option>
          <option value="idea">Идея</option>
        </select>
      </label>
      <label>
        <span>Субъект</span>
        <select v-model="form.subjectId" class="input" required>
          <option value="">—</option>
          <option v-for="item in (form.subjectType === 'project' ? projects : ideas)" :key="item.id" :value="item.id">
            {{ item.title }}
          </option>
        </select>
      </label>
      <label>
        <span>Сфера жизни</span>
        <select v-model="form.sphereId" class="input" required>
          <option value="">—</option>
          <option v-for="sphere in spheres" :key="sphere.id" :value="sphere.id">{{ sphere.name }}</option>
        </select>
      </label>
      <label>
        <span>Влияние</span>
        <select v-model="form.impact" class="input">
          <option value="POSITIVE">+ поддерживает</option>
          <option value="NEUTRAL">0 нейтрально</option>
          <option value="NEGATIVE">− требует ресурса</option>
        </select>
      </label>
      <div class="form-actions">
        <button type="button" class="btn btn-ghost" :disabled="busy" @click="showForm = false">Отмена</button>
        <button type="submit" class="btn btn-primary" :disabled="busy || !form.subjectId || !form.sphereId">Добавить</button>
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

    <div v-if="loading" class="loading">Загрузка…</div>

    <ul v-else-if="rows.length" class="synergy-list">
      <li v-for="row in rows" :key="row.id" class="synergy-row">
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
            :disabled="busy"
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
            :disabled="busy"
            @confirm="remove(row)"
          />
        </div>
      </li>
    </ul>

    <p v-else class="hint">Связей синергии пока нет.</p>
  </div>
</template>

<style scoped>
.synergy-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

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

.form-actions { display: flex; gap: 1rem; margin-left: auto; }

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
</style>
