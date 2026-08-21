<script setup>
/**
 * Управление потоком → Сферы жизни (ticket 09).
 * Ruled reference list: name + concise description. Spheres are reference data,
 * never a score or judgement of the user.
 */
import { computed, onMounted, ref } from 'vue'
import { apiBase, authHeaders } from '../api'
import ConfirmInline from './ConfirmInline.vue'

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
    if (!res.ok) throw new Error(`Сферы жизни: HTTP ${res.status}`)
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

onMounted(load)
</script>

<template>
  <div class="spheres-page">
    <header class="page-header spheres-header">
      <div>
        <h1>Сферы жизни</h1>
        <p class="eyebrow">Управление потоком · справочник сфер</p>
      </div>
      <div class="spheres-actions">
        <label class="inline-check">
          <input v-model="showArchived" type="checkbox" />
          <span>Показывать архивные</span>
        </label>
        <button v-if="!showForm" type="button" class="btn btn-primary" :disabled="busy" @click="startCreate">Добавить сферу</button>
      </div>
    </header>

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

    <p v-else class="hint">Сферы жизни пока не заданы.</p>
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

@media (max-width: 640px) {
  .sphere-row { grid-template-columns: 1fr; }
  .sphere-row-actions { justify-content: flex-start; }
}
</style>
