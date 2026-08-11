<script setup>
import { ref, onMounted } from 'vue'
import { apiBase } from '../api'

const settings = ref({
  timezone: 'Europe/Moscow',
  nightStart: '23:00',
  nightEnd: '07:00',
  dayEnd: '02:00',
  defaultSleepEnd: '09:00',
  hourAccountingMode: 'PRIMARY_ONLY'
})

const loading = ref(false)
const error = ref('')
const success = ref('')

const timezones = [
  'Europe/Moscow',
  'Europe/Paris',
  'Europe/Berlin',
  'Europe/London',
  'America/New_York',
  'America/Los_Angeles',
  'Asia/Tokyo',
  'Asia/Shanghai',
  'Australia/Sydney',
  'UTC'
]

const hourAccountingModes = [
  { value: 'PRIMARY_ONLY', label: 'Только основной проект (по умолчанию)' },
  { value: 'ALL_PROJECTS', label: 'Все проекты' }
]

async function loadSettings() {
  loading.value = true
  error.value = ''
  try {
    const token = localStorage.getItem('wolf_token')
    if (!token) {
      window.location.hash = '#/login'
      return
    }

    const res = await fetch(`${apiBase()}/settings`, {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    })

    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`)
    }

    const data = await res.json()
    settings.value = {
      timezone: data.timezone,
      nightStart: data.nightStart?.slice(0, 5) || '23:00',
      nightEnd: data.nightEnd?.slice(0, 5) || '07:00',
      dayEnd: data.dayEnd?.slice(0, 5) || '02:00',
      defaultSleepEnd: data.defaultSleepEnd?.slice(0, 5) || '09:00',
      hourAccountingMode: data.hourAccountingMode
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function saveSettings() {
  loading.value = true
  error.value = ''
  success.value = ''
  try {
    const token = localStorage.getItem('wolf_token')
    if (!token) {
      window.location.hash = '#/login'
      return
    }

    const res = await fetch(`${apiBase()}/settings`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify(settings.value)
    })

    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`)
    }

    success.value = 'Настройки сохранены'
    setTimeout(() => { success.value = '' }, 3000)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

onMounted(loadSettings)
</script>

<template>
  <div class="settings-page">
    <header class="page-header">
      <h1>Настройки</h1>
      <p class="eyebrow">Часовой пояс, конец дня, ночные часы, режим учёта</p>
    </header>

    <section class="card">
      <div v-if="loading" class="loading">Загрузка…</div>

      <div v-else class="settings-form">
        <div class="form-group">
          <label for="timezone">Часовой пояс</label>
          <select
            id="timezone"
            v-model="settings.timezone"
            :disabled="loading"
            class="input"
          >
            <option v-for="tz in timezones" :key="tz" :value="tz">{{ tz }}</option>
          </select>
          <p class="hint">Используется для границ дня и недели в календаре и Гантте</p>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="dayEnd">Конец дня</label>
            <input
              id="dayEnd"
              type="time"
              v-model="settings.dayEnd"
              :disabled="loading"
              class="input"
            />
            <p class="hint">Граница логических суток (например 02:00). Дела после полуночи до этого времени относятся к предыдущему календарному дню сетки.</p>
          </div>
          <div class="form-group">
            <label for="defaultSleepEnd">Конец авто-Сна</label>
            <input
              id="defaultSleepEnd"
              type="time"
              v-model="settings.defaultSleepEnd"
              :disabled="loading"
              class="input"
            />
            <p class="hint">Интервал «Сон» по умолчанию: от конца дня до этого времени (напр. 02:00–09:00)</p>
          </div>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="nightStart">Начало ночных часов</label>
            <input
              id="nightStart"
              type="time"
              v-model="settings.nightStart"
              :disabled="loading"
              class="input"
            />
            <p class="hint">Строки с этого времени скрываются по умолчанию</p>
          </div>

          <div class="form-group">
            <label for="nightEnd">Конец ночных часов</label>
            <input
              id="nightEnd"
              type="time"
              v-model="settings.nightEnd"
              :disabled="loading"
              class="input"
            />
            <p class="hint">До этого времени строки скрыты (не включительно)</p>
          </div>
        </div>

        <div class="form-group">
          <label for="hourAccountingMode">Режим учёта часов</label>
          <select
            id="hourAccountingMode"
            v-model="settings.hourAccountingMode"
            :disabled="loading"
            class="input"
          >
            <option v-for="mode in hourAccountingModes" :key="mode.value" :value="mode.value">{{ mode.label }}</option>
          </select>
          <p class="hint">
            <strong>Только основной проект:</strong> часы засчитываются только в Основной проект Дела (по умолчанию).<br>
            <strong>Все проекты:</strong> часы засчитываются во все привязанные проекты Дела.
          </p>
        </div>

        <div class="form-actions">
          <button
            @click="saveSettings"
            :disabled="loading"
            class="btn btn-primary"
          >
            <span v-if="loading">Сохранение…</span>
            <span v-else>Сохранить</span>
          </button>
        </div>

        <div v-if="error" class="alert alert-error">{{ error }}</div>
        <div v-if="success" class="alert alert-success">{{ success }}</div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.settings-page {
  max-width: 720px;
}

.settings-form {
  display: grid;
  gap: 1.5rem;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.5rem;
}

@media (max-width: 560px) {
  .form-row {
    grid-template-columns: 1fr;
  }
}
</style>