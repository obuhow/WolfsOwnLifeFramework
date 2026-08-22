<script setup>
import { ref, onMounted } from 'vue'
import { apiBase } from '../api'
import ConfirmInline from './ConfirmInline.vue'

const areas = ref([])
const loading = ref(false)
const error = ref('')
const success = ref('')
const editingId = ref(null)
const form = ref({ name: '', color: '#3d5a4a' })
const showForm = ref(false)

const defaultColors = [
  '#3d5a4a', // green
  '#8a4b2f', // terracotta
  '#4a5d7a', // blue-gray
  '#7a5a4a', // brown
  '#5a4a7a', // purple
  '#4a7a5a', // teal
  '#7a6a4a', // olive
  '#a85a5a', // muted red
]

async function loadAreas() {
  loading.value = true
  error.value = ''
  try {
    const token = localStorage.getItem('wolf_token')
    if (!token) {
      window.location.href = '#/login'
      return
    }

    const res = await fetch(`${apiBase()}/life-areas`, {
      headers: { 'Authorization': `Bearer ${token}` }
    })

    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`)
    }

    areas.value = await res.json()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.value = { name: '', color: defaultColors[areas.value.length % defaultColors.length] }
  showForm.value = true
}

function openEdit(area) {
  editingId.value = area.id
  form.value = { name: area.name, color: area.color || '#3d5a4a' }
  showForm.value = true
}

function cancelForm() {
  showForm.value = false
  editingId.value = null
  form.value = { name: '', color: '#3d5a4a' }
}

async function saveArea() {
  if (!form.value.name.trim()) {
    error.value = 'Название обязательно'
    return
  }

  loading.value = true
  error.value = ''
  success.value = ''

  try {
    const token = localStorage.getItem('wolf_token')
    if (!token) {
      window.location.href = '#/login'
      return
    }

    const isEdit = editingId.value !== null
    const url = isEdit
      ? `${apiBase()}/life-areas/${editingId.value}`
      : `${apiBase()}/life-areas`
    const method = isEdit ? 'PUT' : 'POST'

    const res = await fetch(url, {
      method,
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify(form.value)
    })

    if (!res.ok) {
      const data = await res.json().catch(() => ({}))
      throw new Error(data.message || `HTTP ${res.status}`)
    }

    success.value = isEdit ? 'Область обновлена' : 'Область создана'
    cancelForm()
    await loadAreas()
    setTimeout(() => { success.value = '' }, 3000)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function deleteArea(area) {

  loading.value = true
  error.value = ''
  success.value = ''

  try {
    const token = localStorage.getItem('wolf_token')
    if (!token) {
      window.location.href = '#/login'
      return
    }

    const res = await fetch(`${apiBase()}/life-areas/${area.id}`, {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${token}` }
    })

    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`)
    }

    success.value = 'Область удалена'
    await loadAreas()
    setTimeout(() => { success.value = '' }, 3000)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function moveArea(area, direction) {
  const idx = areas.value.findIndex(a => a.id === area.id)
  if (idx < 0) return

  const newIndex = direction === 'up' ? idx - 1 : idx + 1
  if (newIndex < 0 || newIndex >= areas.value.length) return

  loading.value = true
  error.value = ''

  try {
    const token = localStorage.getItem('wolf_token')
    if (!token) {
      window.location.href = '#/login'
      return
    }

    const res = await fetch(`${apiBase()}/life-areas/${area.id}/move`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({ newIndex })
    })

    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`)
    }

    await loadAreas()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

onMounted(loadAreas)
</script>

<template>
  <div class="life-areas-page">
    <header class="page-header">
      <h1>Области жизни</h1>
      <p class="eyebrow">Рамка вашей жизни — работа, здоровье, семья, деньги, отдых…</p>
    </header>

    <div v-if="error" class="alert alert-error" style="margin-bottom: 1.5rem">{{ error }}</div>
    <div v-if="success" class="alert alert-success" style="margin-bottom: 1.5rem">{{ success }}</div>

    <!-- Create / Edit Form -->
    <section v-if="showForm" class="card" style="margin-bottom: 1.5rem">
      <h2>{{ editingId ? 'Переименовать область' : 'Новая область жизни' }}</h2>
      <form @submit.prevent="saveArea" class="life-area-form">
        <div class="form-group">
          <label for="area-name">Название</label>
          <input
            id="area-name"
            type="text"
            v-model="form.name"
            :disabled="loading"
            class="input"
            placeholder="Например: Работа, Здоровье, Семья…"
            maxlength="100"
            required
            autofocus
          />
        </div>

        <div class="form-group">
          <label for="area-color">Цвет</label>
          <div style="display: flex; gap: 0.75rem; align-items: center; flex-wrap: wrap">
            <input
              id="area-color"
              type="color"
              v-model="form.color"
              :disabled="loading"
              style="width: 48px; height: 36px; border: 1px solid #d9d1c6; border-radius: 8px; cursor: pointer; padding: 2px"
            />
            <div style="display: flex; gap: 0.4rem; flex-wrap: wrap">
              <button
                v-for="c in defaultColors"
                :key="c"
                type="button"
                @click="form.color = c"
                :style="{
                  width: '28px',
                  height: '28px',
                  borderRadius: '6px',
                  border: form.color === c ? '2px solid #2c2a26' : '1px solid #d9d1c6',
                  background: c,
                  cursor: 'pointer',
                  padding: 0
                }"
                :aria-label="`Выбрать цвет ${c}`"
              />
            </div>
          </div>
        </div>

        <div class="form-actions">
          <button type="submit" :disabled="loading" class="btn btn-primary">
            <span v-if="loading">Сохранение…</span>
            <span v-else>{{ editingId ? 'Сохранить' : 'Создать' }}</span>
          </button>
          <button type="button" @click="cancelForm" :disabled="loading" class="btn btn-ghost">
            Отмена
          </button>
        </div>
      </form>
    </section>

    <!-- List -->
    <section class="card">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.25rem">
        <h2 style="margin: 0">Ваши области</h2>
        <button v-if="!showForm" @click="openCreate" class="btn btn-primary" :disabled="loading">
          + Добавить
        </button>
      </div>

      <div v-if="loading && areas.length === 0" class="loading">Загрузка…</div>

      <div v-else-if="areas.length === 0" class="empty-state">
        <h3>Пока пусто</h3>
        <p>Создайте первую область жизни — рамку, в которую лягут проекты и дела.</p>
        <button @click="openCreate" class="btn btn-primary">Создать область</button>
      </div>

      <div v-else class="life-areas-list">
        <div
          v-for="(area, index) in areas"
          :key="area.id"
          class="life-area-item"
        >
          <div
            class="life-area-color"
            :style="{ background: area.color || '#3d5a4a' }"
            :title="area.color"
          />
          <span class="life-area-name">{{ area.name }}</span>
          <div class="life-area-actions">
            <button
              class="icon-btn"
              @click="moveArea(area, 'up')"
              :disabled="loading || index === 0"
              title="Выше"
              aria-label="Переместить выше"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="18 15 12 9 6 15"></polyline>
              </svg>
            </button>
            <button
              class="icon-btn"
              @click="moveArea(area, 'down')"
              :disabled="loading || index === areas.length - 1"
              title="Ниже"
              aria-label="Переместить ниже"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="6 9 12 15 18 9"></polyline>
              </svg>
            </button>
            <button
              class="icon-btn"
              @click="openEdit(area)"
              :disabled="loading"
              title="Переименовать"
              aria-label="Переименовать"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
              </svg>
            </button>
            <ConfirmInline
              label="Удалить"
              :question="`Удалить область «${area.name}»?`"
              confirm-label="Да, удалить"
              :disabled="loading"
              @confirm="deleteArea(area)"
            />
          </div>
        </div>
      </div>
    </section>
  </div>
</template>