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
import { computed, onMounted, ref, watch } from 'vue'
import { apiBase } from '../api'
import ConfirmInline from './ConfirmInline.vue'

const props = defineProps({
  projectId: { type: [Number, String], default: null },
  deloId: { type: [Number, String], default: null }
})

const notes = ref([])
const loading = ref(false)
const error = ref('')
const editingId = ref(null)
const form = ref({ body: '', tags: '' })
const filters = ref({ author: '', tag: '', q: '' })

const parentId = computed(() => props.projectId ?? props.deloId)
const parentParam = computed(() => props.projectId != null ? 'projectId' : 'deloId')
const editing = computed(() => editingId.value != null)

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

function splitTags(value) {
  return value.split(',').map(tag => tag.trim()).filter(Boolean)
}

function tagsText(tags) {
  return (tags || []).join(', ')
}

function authorLabel(author) {
  return author === 'AGENT' ? 'от агента' : 'моя заметка'
}

function resetForm() {
  editingId.value = null
  form.value = { body: '', tags: '' }
}

function editNote(note) {
  editingId.value = note.id
  form.value = { body: note.body || '', tags: tagsText(note.tags) }
}

function queryString() {
  const params = new URLSearchParams({ [parentParam.value]: String(parentId.value) })
  if (filters.value.author) params.set('author', filters.value.author)
  if (filters.value.tag.trim()) params.set('tag', filters.value.tag.trim())
  if (filters.value.q.trim()) params.set('q', filters.value.q.trim())
  return params.toString()
}

async function loadNotes() {
  const headers = authHeaders()
  if (!headers || parentId.value == null) return
  loading.value = true
  error.value = ''
  try {
    const res = await fetch(`${apiBase()}/notes?${queryString()}`, { headers })
    if (!res.ok) throw new Error(`Заметки: HTTP ${res.status}`)
    notes.value = await res.json()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function saveNote() {
  if (!form.value.body.trim()) {
    error.value = 'Текст заметки обязателен'
    return
  }
  const headers = authHeaders(true)
  if (!headers) return
  loading.value = true
  error.value = ''
  try {
    const body = {
      [parentParam.value]: Number(parentId.value),
      body: form.value.body.trim(),
      tags: splitTags(form.value.tags)
    }
    const url = editing.value ? `${apiBase()}/notes/${editingId.value}` : `${apiBase()}/notes`
    const res = await fetch(url, { method: editing.value ? 'PUT' : 'POST', headers, body: JSON.stringify(body) })
    if (!res.ok) {
      const data = await res.json().catch(() => ({}))
      throw new Error(data.message || `HTTP ${res.status}`)
    }
    resetForm()
    await loadNotes()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
    loading.value = false
  }
}

async function removeNote(note) {
  const headers = authHeaders()
  if (!headers) return
  loading.value = true
  error.value = ''
  try {
    const res = await fetch(`${apiBase()}/notes/${note.id}`, { method: 'DELETE', headers })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    await loadNotes()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
    loading.value = false
  }
}

watch(() => [props.projectId, props.deloId, filters.value.author, filters.value.tag, filters.value.q], loadNotes)
onMounted(loadNotes)
</script>

<template>
  <section class="card notes-panel">
    <div class="projects-toolbar notes-toolbar">
      <h2 style="margin: 0">Заметки</h2>
      <span class="muted">{{ notes.length }}</span>
    </div>

    <div v-if="error" class="alert alert-error notes-alert">{{ error }}</div>

    <form class="notes-form" @submit.prevent="saveNote">
      <label class="sr-only" for="note-body">Текст заметки</label>
      <textarea id="note-body" v-model="form.body" class="input textarea" rows="4" maxlength="100000" placeholder="Markdown-текст заметки…" :disabled="loading" />
      <div class="notes-form-row">
        <label class="sr-only" for="note-tags">Теги</label>
        <input id="note-tags" v-model="form.tags" class="input" maxlength="1000" placeholder="теги через запятую" :disabled="loading" />
        <div class="form-actions">
          <button type="submit" class="btn btn-primary" :disabled="loading">{{ editing ? 'Сохранить' : 'Добавить заметку' }}</button>
          <button v-if="editing" type="button" class="btn btn-ghost" :disabled="loading" @click="resetForm">Отмена</button>
        </div>
      </div>
    </form>

    <div class="notes-filters">
      <input v-model="filters.q" class="input" placeholder="поиск по тексту" aria-label="Поиск заметок" />
      <input v-model="filters.tag" class="input" placeholder="фильтр по тегу" aria-label="Фильтр заметок по тегу" />
      <select v-model="filters.author" class="input" aria-label="Фильтр заметок по автору">
        <option value="">все авторы</option>
        <option value="USER">мои</option>
        <option value="AGENT">от агента</option>
      </select>
    </div>

    <div v-if="loading && !notes.length" class="loading">Загрузка заметок…</div>
    <div v-else-if="!notes.length" class="muted-block">Пока нет заметок.</div>
    <div v-else class="notes-list">
      <article v-for="note in notes" :key="note.id" class="note-item" :class="{ 'note-agent': note.author === 'AGENT' }">
        <div class="note-meta">
          <span class="note-author"><span aria-hidden="true">{{ note.author === 'AGENT' ? '✦' : '·' }}</span> {{ authorLabel(note.author) }}</span>
          <span class="muted">{{ note.createdAt ? new Date(note.createdAt).toLocaleString('ru-RU') : '' }}</span>
        </div>
        <div class="note-body">{{ note.body }}</div>
        <div class="note-footer">
          <div class="note-tags">
            <span v-for="tag in note.tags" :key="tag" class="note-tag">#{{ tag }}</span>
          </div>
          <div class="note-actions">
            <button class="btn btn-ghost" :disabled="loading" @click="editNote(note)">Изменить</button>
            <ConfirmInline label="Удалить" question="Удалить заметку?" confirm-label="Да, удалить" :disabled="loading" @confirm="removeNote(note)" />
          </div>
        </div>
      </article>
    </div>
  </section>
</template>
