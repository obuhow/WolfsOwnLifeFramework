<script setup>
import { ref, onMounted } from 'vue'
import { apiBase } from '../api'

const settings = ref({
  timezone: 'Europe/Moscow',
  nightStart: '23:00',
  nightEnd: '07:00',
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
      window.location.href = '/login.html'
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
      window.location.href = '/login.html'
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
    <header class="header">
      <h1>Настройки</h1>
    </header>

    <main class="main">
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
    </main>
  </div>
</template>

<style scoped>
.settings-page {
  max-width: 720px;
  margin: 0 auto;
  padding: 2rem 1.5rem 3rem;
}

.header {
  margin-bottom: 2rem;
}

.header h1 {
  margin: 0;
  font-size: clamp(1.8rem, 4vw, 2.4rem);
  font-weight: 600;
  letter-spacing: -0.02em;
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

.form-group {
  display: grid;
  gap: 0.4rem;
}

.form-group label {
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #8a8278;
  font-weight: 500;
}

.input {
  font-family: inherit;
  font-size: 1rem;
  padding: 0.65rem 0.9rem;
  border: 1px solid #d9d1c6;
  border-radius: 10px;
  background: #fdfbf7;
  color: #2c2a26;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.input:focus {
  outline: none;
  border-color: #a89a88;
  box-shadow: 0 0 0 3px rgba(168, 154, 136, 0.15);
}

.input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.hint {
  margin: 0;
  font-size: 0.8rem;
  color: #7a7268;
  line-height: 1.4;
}

.hint strong {
  color: #5c564e;
}

.form-actions {
  display: flex;
  gap: 0.75rem;
  margin-top: 0.5rem;
}

.btn {
  font-family: inherit;
  font-size: 0.95rem;
  font-weight: 500;
  padding: 0.7rem 1.4rem;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s, transform 0.05s;
}

.btn:active:not(:disabled) {
  transform: scale(0.98);
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed.
}

.btn-primary {
  background: #3d5a4a;
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  background: #2f4a3c;
}

.alert {
  padding: 0.75rem 1rem;
  border-radius: 10px;
  font-size: 0.9rem;
}

.alert-error {
  background: #fdeaea;
  border: 1px solid #e8b4b4;
  color: #8a3a3a;
}

.alert-success {
  background: #e6f4ea;
  border: 1px solid #a8d5a8;
  color: #2a6b3a;
}

.loading {
  text-align: center;
  padding: 3rem;
  color: #7a7268;
}
</style>